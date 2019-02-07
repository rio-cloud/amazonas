/*
 * Copyright 2019 TB Digital Services GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cloud.rio.amazonas

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.codepipeline.AWSCodePipeline
import com.amazonaws.services.codepipeline.AWSCodePipelineClientBuilder
import com.amazonaws.services.codepipeline.model.GetPipelineExecutionRequest
import com.amazonaws.services.codepipeline.model.PipelineExecutionNotFoundException
import com.amazonaws.services.codepipeline.model.StartPipelineExecutionRequest
import org.apache.logging.log4j.LogManager

class CodePipelineRunner(
        private val awsCodePipeline: AWSCodePipeline
) {
    /**
     * Instantiates an AWSCodepipelineClient with credentials and region. With an instance of the client you start a
     * codepipeline and in case of a failure of this pipeline, the instance will throw an exception.
     *
     * @param credentialsProvider an AWSCredentialsProvider, e.g. the MfaCredentialsProvider
     * @param region              the AWS region that you want your stacks to be deployed to
     */
    constructor(credentialsProvider: AWSCredentialsProvider, region: String) : this(AWSCodePipelineClientBuilder
            .standard()
            .withRegion(region)
            .withCredentials(credentialsProvider)
            .build())

    /**
     * Starts the specified pipeline, waits until it has reached its final status. In case this is a failed status,
     * it will throw an exception
     *
     * @param pipelineName           the name of the pipeline
     * @param sleepWhileWaitingInSec the number of seconds the waitForFinalPipelineStatus method should sleep before
     *                               checking the pipeline status again
     */
    fun startPipelineAndWaitForSuccess(
            pipelineName: String,
            sleepWhileWaitingInSec: Int = 10) {
        val pipelineExecutionId = startPipelineAndReturnExecutionId(pipelineName)
        val finalPipelineStatus = waitForFinalPipelineStatus(pipelineName, pipelineExecutionId, sleepWhileWaitingInSec)
        if (finalPipelineStatus in failedPipelineStatuses) {
            throw PipelineFailedException("Pipeline $pipelineName has final status $finalPipelineStatus")
        }
    }

    private fun startPipelineAndReturnExecutionId(pipelineName: String): String {
        val startPipelineExecutionResult = awsCodePipeline.startPipelineExecution(
                StartPipelineExecutionRequest()
                        .withName(pipelineName)
        )
        return startPipelineExecutionResult.pipelineExecutionId
    }

    private fun retrieveCurrentPipelineStatus(pipelineName: String, pipelineExecutionId: String, loopWait: Int): String {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() < start + retrieveCurrentPipelineStatusTimeout * 1000) {
            try {
                Thread.sleep((loopWait * 1000).toLong())
                val getPipelineExecutionResult = awsCodePipeline.getPipelineExecution(
                        GetPipelineExecutionRequest()
                                .withPipelineName(pipelineName)
                                .withPipelineExecutionId(pipelineExecutionId)
                )
                return getPipelineExecutionResult.pipelineExecution.status
            } catch (e: PipelineExecutionNotFoundException) {
                LOGGER.info(e)
            }
        }
        throw PipelineExecutionNotFoundException("Can't find execution id $pipelineExecutionId for pipeline $pipelineName after $retrieveCurrentPipelineStatusTimeout secs.")
    }

    private fun waitForFinalPipelineStatus(pipelineName: String, pipelineExecutionId: String, loopWait: Int): String {
        var pipelineFinished = false
        var finalPipelineStatus = failedPipelineStatuses[0]
        val start = System.currentTimeMillis()

        while (!pipelineFinished) {
            if (System.currentTimeMillis() > start + waitForFinalPipelineStatusTimeout * 1000) throw RuntimeException("Timeout")
            val pipelineStatus = retrieveCurrentPipelineStatus(pipelineName, pipelineExecutionId, loopWait)
            LOGGER.info("Current status of $pipelineName is $pipelineStatus")
            if (pipelineStatus != waitingPipelineStatus) {
                pipelineFinished = true
                finalPipelineStatus = pipelineStatus
            } else {
                Thread.sleep((loopWait * 1000).toLong())
            }
        }
        return finalPipelineStatus
    }

    companion object {
        private const val waitingPipelineStatus = "InProgress"
        private val failedPipelineStatuses = listOf(
                "Failed",
                "Superseded"
        )
        private const val waitForFinalPipelineStatusTimeout = 10 * 60//sec
        private const val retrieveCurrentPipelineStatusTimeout = 1 * 60//sec
        private val LOGGER = LogManager.getLogger(CodePipelineRunner::class.java)
    }
}

class PipelineFailedException internal constructor(message: String) : RuntimeException(message)
