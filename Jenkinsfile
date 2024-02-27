pipeline {
    agent any

    stages {
    /*
        stage('Checkout') {
            steps {
                git(credentialsId: 'fe447ebb-288c-406b-a01b-f84b72275287',
                    url: 'git@github.com:TONTIX/stoneticket.git',
                    branch: 'main')
            }
        }
    */

        stage('Build & Push Container Image') {
            steps {
                script {
                    docker.withRegistry("https://registry-intl-vpc.eu-central-1.aliyuncs.com", "e27de337-f03c-4a9e-a6bd-06d47cba1fa6") {

                        def dockerImage = docker.build("registry-intl-vpc.eu-central-1.aliyuncs.com/tontix/stoneticket-service:latest", ".")
                        dockerImage.tag("${BUILD_NUMBER}")
                        dockerImage.push("latest")
                        dockerImage.push("${BUILD_NUMBER}")
                    }
                }
            }
        }

        stage('Setup Kubectl') {
            steps {
                script {
                    // Determine the latest version of kubectl
                    def kubectlVersion = sh(script: "curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt", returnStdout: true).trim()
                    def kubectlDownloadUrl = "https://storage.googleapis.com/kubernetes-release/release/${kubectlVersion}/bin/linux/amd64/kubectl"
                    sh "curl -LO ${kubectlDownloadUrl}"
                    sh "chmod +x ./kubectl"
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                script {
                    withKubeConfig([credentialsId: 'stoneticket-dev-kubeconfig']) {
                        sh './kubectl config set-context --current --namespace=tontix'
                        sh './kubectl apply -f ./kubernetes/.'
                        sh './kubectl rollout restart deployment/stoneticket'
                    }
                }
            }
        }

        stage('Cleanup the container context') {
            steps {
                script {
                    sh 'docker system prune -a -f'
                }
            }
        }
    }
}
