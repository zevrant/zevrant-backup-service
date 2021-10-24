
String branchName = (BRANCH_NAME.startsWith('PR-')) ? CHANGE_BRANCH : BRANCH_NAME

pipeline {
    agent {
        kubernetes {
            inheritFrom 'jnlp'
        }
    }
    stages {
        stage('Launch Build') {
            steps {
                script {
                    String[] jobNamePieces = JOB_NAME.split('/')
                    echo "$jobNamePieces"
                    String[] appPieces = jobNamePieces[1].split(" ")
                    echo "$appPieces"
                    String applicationType = appPieces[0]
                    String folderName = "${appPieces[0].capitalize()} ${appPieces[1].capitalize()} ${appPieces[2].capitalize()}"
                    String repository = folderName.replace(" ", "-").toLowerCase()
                    build(
                            job: "${applicationType}/${folderName}/$repository",
                            propagate: true,
                            wait: true,
                            parameters: [
                                    [$class: 'StringParameterValue', name: 'BRANCH_NAME', value: branchName],
                            ]
                    )
                }
            }
        }
    }
}