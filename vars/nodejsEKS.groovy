def call(Map configMap){
    pipeline {
    agent {
        label 'AGENT-1'
    }
    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds() // No Multiple  Builds
        ansiColor('xterm')
    }
nment{
        def appVersion = '' // variable declaration in GLOBAL LEVEL
        nexusUrl = pipelineGlobals.nexusURL()
        region = pipelineGlobals.region()
        account_id = pipelineGlobals.account_id()
        component = configMap.get("component")
        project = configMap.get("project")
        // def releaseExists = ""
    }
    stages {
        stage ('read the version'){
            steps{ // Variable can be accessed with in that stage only
                script{ // Groovy Script
                def packageJson = readJSON file: 'package.json'
                appVersion = packageJson.version
                echo "application version: $appVersion"
                }
            }
        }
        stage('Install Dependencies') { // init should happen whether apply or destroy
            steps {
               sh """
                npm install
                ls -ltr
                echo "application version: $appVersion"
               """
            }
        }
        stage('Build'){ // Build == Dependencies + code (zipped)
            steps{
                sh """
                sudo yum install zip -y
                zip -q -r ${component}-${appVersion}.zip * -x Jenkinsfile -x ${component}-${appVersion}.zip
                ls -ltr
                """
            } // -q (quit --> No need of un-necessary log in jenkins )   -x exclude those files
        }

        stage('Docker build'){
            steps{
                sh """
                    aws ecr get-login-password --region ${region} | docker login --username AWS --password-stdin ${account_id}.dkr.ecr.${region}.amazonaws.com

                    docker build -t ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion} .

                    docker push ${account_id}.dkr.ecr.${region}.amazonaws.com/${project}-${component}:${appVersion}
                """
            }
        }

        stage('Deploy'){
            steps{
                sh """
                    aws eks update-kubeconfig --region ${region} --name ${project}-dev
                    cd helm
                    sed -i 's/IMAGE_VERSION/${appVersion}/g' values.yaml
                    helm upgrade ${component} -n expense .
                """
            } // 1st helm install backend .   2nd helm upgrade backend .
        }

        
        // stage("Quality Gate") {
        //     steps {
        //       timeout(time: 30, unit: 'MINUTES') {
        //         waitForQualityGate abortPipeline: true // --? If Quality Gate Fail then fail the PipeLine
        //       }
        //     }
        // }


        //  stage('Nexus Artifact Upload'){
        //     steps{
        //         script{ // Groovy Script for Jenkins
        //             nexusArtifactUploader( // --> This is plugin below code from internet
        //                 nexusVersion: 'nexus3',
        //                 protocol: 'http',
        //                 nexusUrl: "${nexusUrl}", // double quotes --> When using variables
        //                 groupId: 'com.expense',
        //                 version: "${appVersion}",
        //                 repository: "backend",
        //                 credentialsId: 'nexus-auth', 
        //                 // Created in Jenkins -->Manage Jenkins --> Credentials --> System --> Global credentials (unrestricted)
        //                 artifacts: [
        //                     [artifactId: "backend",
        //                     classifier: '',
        //                     file: "backend-" + "${appVersion}" + '.zip', // filename: backend-1.1.0.zip
        //                     type: 'zip']
        //                 ]
        //             )
        //         }
        //     }
        // }

     }
    post {  //This will catch the event and send Alerts to Mail/Slack
        always { 
            echo 'I will always say Hello again!'
            deleteDir()
        }
        success { 
            echo 'I will run when pipeline is success'
        }
        failure { 
            echo 'I will run when pipeline is failure'
        }
    }
}
}