package com.zevrant.services.zevrantbackupservice.services;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.zevrant.services.zevrantbackupservice.comparators.VersionComparator;
import com.zevrant.services.zevrantbackupservice.rest.UpdateCheckResponse;
import net.zevrant.services.security.common.secrets.management.services.AwsSessionCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UpdateService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);

    private final String bucketName;
    private final String folder;
    private final AwsSessionCredentialsProvider sessionCredentialsProvider;

    @Autowired
    public UpdateService(@Value("${zevrant.s3.apk.bucketName}") String bucketName,
                         @Value("${spring.profiles.active}") String profile) {
        sessionCredentialsProvider = new AwsSessionCredentialsProvider();
        this.bucketName = bucketName;
        List<String> profileList = Arrays.asList(profile.split(","));
        if (profileList.stream().anyMatch(activeProfile -> activeProfile.equals("local"))) {
            this.folder = "local";
        } else if (profileList.stream().anyMatch(activeProfile -> activeProfile.equals("develop"))) {
            this.folder = "develop";
        } else if (profileList.stream().anyMatch(activeProfile -> activeProfile.equals("prod"))) {
            this.folder = "release";
        } else {
            throw new RuntimeException("Cannot determine apk file location from active profiles no active profiles found");
        }

    }

    public UpdateCheckResponse checkForUpdate(String currentVersion) {
        VersionComparator versionComparator = new VersionComparator();
        logger.debug("looking for folder {}", folder);
        AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1)
                .build();
        List<String> versions = s3.listObjects(bucketName).getObjectSummaries()
                .stream().filter(s3ObjectSummary -> s3ObjectSummary.getKey().contains(folder))
                .map(s3ObjectSummary -> s3ObjectSummary.getKey().split("/")[1])
                .sorted(versionComparator)
                .collect(Collectors.toList());

        logger.debug("versions found: {}", versions);
        if (versions.isEmpty()) {
            throw new RuntimeException("No versions found for folder ".concat(folder));
        }
        String latestVersion = versions.get(versions.size() - 1);
        logger.debug("version comparison == {} ", versionComparator.compare(latestVersion, currentVersion));
        return new UpdateCheckResponse(currentVersion, latestVersion, versionComparator.compare(latestVersion, currentVersion) > 0);
//            return new UpdateCheckResponse(currentVersion, "latestVersion", versionComparator.compare(currentVersion, "0.0.0") > 0);
    }

    public S3ObjectInputStream getApk(String version) {
        BasicSessionCredentials sessionCredentials = sessionCredentialsProvider.assumeRole(Regions.US_EAST_1.name(), System.getenv("ROLE_ARN"));
        AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
                .build();
        String key = this.folder.concat("/").concat(version).concat("/zevrant-services.apk");
        logger.debug("requesting s3://{}{}", this.bucketName, key);
        S3Object apk = s3.getObject(this.bucketName, key);
        return apk.getObjectContent();
    }
}
