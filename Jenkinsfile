pipeline {
    agent any

    stages {

        stage('Checkout') {
            steps {
                git(credentialsId: '',
                    url: 'https://github.com/grantsunny/JTicket.git',
                    branch: 'main')
            }
        }

        stage('Build & Push Container Image') {
            steps {
                script {
                    docker.withRegistry("", "") {

                        def dockerImage = docker.build("jticket/ticket-service:latest", ".")
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
                        sh './kubectl rollout restart deployment/JTicket'
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
