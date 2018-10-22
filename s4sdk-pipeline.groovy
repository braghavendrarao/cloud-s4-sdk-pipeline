#!/usr/bin/env groovy

final def pipelineSdkVersion = 'v12'

pipeline {
    agent any
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        skipDefaultCheckout()
    }
    stages {
        stage('Init') {
            steps {
                library "s4sdk-pipeline-library@${pipelineSdkVersion}"
                stageInitS4sdkPipeline script: this
                abortOldBuilds script: this
            }
        }

        stage('Build') {
            steps {
                stageBuild script: this
            }
        }

        stage('Local Tests') {
            parallel {
                stage("Static Code Checks") { steps { stageStaticCodeChecks script: this } }
                stage("Backend Unit Tests") { steps { stageUnitTests script: this } }
                stage("Backend Integration Tests") { steps { stageIntegrationTests script: this } }
                stage("Frontend Unit Tests") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.FRONT_END_TESTS } }
                    steps { stageFrontendUnitTests script: this }
                }
                stage("Node Security Platform Scan") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.NODE_SECURITY_SCAN } }
                    steps { stageNodeSecurityPlatform script: this }
                }
            }
        }

        stage('Remote Tests') {
            when { expression { commonPipelineEnvironment.configuration.runStage.REMOTE_TESTS } }
            parallel {
                stage("End to End Tests") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.E2E_TESTS } }
                    steps { stageEndToEndTests script: this }
                }
                stage("Performance Tests") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.PERFORMANCE_TESTS } }
                    steps { stagePerformanceTests script: this }
                }
            }
        }

        stage('Quality Checks') {
            steps { stageS4SdkQualityChecks script: this }
        }

        stage('Third-party Checks') {
            when { expression { commonPipelineEnvironment.configuration.runStage.THIRD_PARTY_CHECKS } }
            parallel {
                stage("Checkmarx Scan") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.CHECKMARX_SCAN } }
                    steps { stageCheckmarxScan script: this }
                }
                stage("WhiteSource Scan") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.WHITESOURCE_SCAN } }
                    steps { stageWhitesourceScan script: this }
                }
                stage("SourceClear Scan") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.SOURCE_CLEAR_SCAN } }
                    steps { stageSourceClearScan script: this }
                }
                stage("Fortify Scan") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.FORTIFY_SCAN } }
                    steps { stageFortifyScan script: this }
                }
                stage("Additional Tools") {
                    when { expression { commonPipelineEnvironment.configuration.runStage.ADDITIONAL_TOOLS } }
                    steps { stageAdditionalTools script: this }
                }
            }

        }

        stage('Artifact Deployment') {
            when { expression { commonPipelineEnvironment.configuration.runStage.ARTIFACT_DEPLOYMENT } }
            steps { stageArtifactDeployment script: this }
        }

        stage('Production Deployment') {
            when { expression { commonPipelineEnvironment.configuration.runStage.PRODUCTION_DEPLOYMENT } }
            steps { stageProductionDeployment script: this }
        }

    }
    post {
        always {
            script {
                if (commonPipelineEnvironment.configuration.runStage?.SEND_NOTIFICATION) {
                    postActionSendNotification script: this
                }
                sendAnalytics script:this
            }
        }
        failure { deleteDir() }
    }
}
