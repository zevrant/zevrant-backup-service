
String branchName = (BRANCH_NAME.startsWith('PR-')) ? CHANGE_BRANCH : BRANCH_NAME

pipeline {
    agent {
        label 'master'
    }
    stages {
        stage('Launch Build') {
            steps {
                script {
                    String[] appPieces = REPOSITORY.split('-')
                    String folderName = "${appPieces[0].capitalize()} ${appPieces[1].capitalize()} ${appPieces[2].capitalize()}"
                    build(
                            job: "${APPLICATION_TYPE}/${folderName}/$REPOSITORY",
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