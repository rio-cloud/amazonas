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
 *
 * This class is a derivative work of the
 * jp.classmethod.aws.gradle.cloudformation package. It has been migrated
 * to Kotlin, the two classes AmazonCloudFormationMigrateStackTask and
 * AmazonCloudFormationWaitStackStatusTask have been merged into one,
 * and over time several refactorings were performed.
 *
 * The original work was published with the following copyright notice:
 *
 * Copyright 2015-2016 the original author or authors.
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

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.cloudformation.model.*
import org.apache.commons.io.FileUtils
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.IOException

class AmazonCloudformationClient(private val amazonCloudFormation: AmazonCloudFormation) {
    /**
     * Instantiates an AmazonCloudformationClient with credentials and region. With an instance of the client you can
     * easily deploy your AWS CloudFormation stacks to the account specified in the credentials.
     *
     * @param credentialsProvider an AWSCredentialsProvider, e.g. the MfaCredentialsProvider
     * @param region              the AWS region that you want your stacks to be deployed to
     */
    constructor(credentialsProvider: AWSCredentialsProvider, region: String) :
            this(
                    AmazonCloudFormationClientBuilder
                            .standard()
                            .withRegion(region)
                            .withCredentials(credentialsProvider)
                            .build()
            )

    /**
     * Creates or updates the specified CloudFormation stackTemplate and waits for completion.
     *
     * @param stackTemplate          the stackTemplate to be created or updated
     * @param capability             the capability with with the stack should be deployed can be null,
     *                               CAPABILITY_NAMED_IAM or CAPABILITY_IAM
     * @param onCreationFailure      the action that should be taken if the stack creation fails can be DELETE or ROLLBACK
     * @param sleepWhileWaitingInSec the number of seconds the waitForStack method should sleep before checking the
     *                               stack status again
     */
    fun createOrUpdateStackAndWait(
            stackTemplate: StackTemplate,
            capability: String = "CAPABILITY_NAMED_IAM",
            onCreationFailure: String = "DELETE",
            sleepWhileWaitingInSec: Int = 10,
            enableTerminationProtection: Boolean = false
    ) {
        try {
            val stack = amazonCloudFormation
                    .describeStacks(DescribeStacksRequest().withStackName(stackTemplate.name))
                    .stacks
                    .first()
            when {
                stack.stackStatus == "DELETE_COMPLETE" -> deleteAndCreate(stackTemplate, capability, onCreationFailure, sleepWhileWaitingInSec, enableTerminationProtection)
                stableStackStatuses.contains(stack.stackStatus) -> updateStackAndWait(stackTemplate, capability, sleepWhileWaitingInSec, enableTerminationProtection)
                else -> throw RuntimeException("Invalid status for update: ${stack.stackStatus}")
            }
        } catch (e: AmazonServiceException) {
            when {
                e.message!!.contains("does not exist") -> createStackAndWait(stackTemplate, capability, onCreationFailure, sleepWhileWaitingInSec, enableTerminationProtection)
                e.message!!.contains("No updates are to be performed.") -> LOGGER.trace(e.message)
                else -> throw e
            }
        }

    }

    private fun deleteAndCreate(stackTemplate: StackTemplate, capability: String, onCreationFailure: String, loopWaitInSec: Int, enableTerminationProtection: Boolean) {
        LOGGER.info("Deleting stack: ${stackTemplate.name} before it can be created.")
        deleteStackAndWait(stackTemplate.name)
        createStackAndWait(stackTemplate, capability, onCreationFailure, loopWaitInSec, enableTerminationProtection)
        updateTerminationProtection(stackTemplate.name, enableTerminationProtection)
    }

    private fun updateStackAndWait(stackTemplate: StackTemplate, capability: String, loopWaitInSec: Int, enableTerminationProtection: Boolean) {
        LOGGER.info("Update stack: ${stackTemplate.name}")
        updateTerminationProtection(stackTemplate.name, enableTerminationProtection)
        val updateStackResult = updateStack(stackTemplate, capability)
        LOGGER.info("Update requested: ${updateStackResult.stackId}")
        waitForStack(stackTemplate.name, updateStackResult.stackId, loopWaitInSec)
    }

    private fun createStackAndWait(stackTemplate: StackTemplate, capability: String, cfnOnFailure: String, loopWaitInSec: Int, enableTerminationProtection: Boolean) {
        LOGGER.info("Create stack: ${stackTemplate.name}")
        val createStackResult = createStack(stackTemplate, capability, cfnOnFailure)
        LOGGER.info("Create requested: ${createStackResult.stackId}")
        waitForStack(stackTemplate.name, createStackResult.stackId, loopWaitInSec)
        updateTerminationProtection(stackTemplate.name, enableTerminationProtection)
    }

    private fun deleteStackAndWait(stackName: String): DeleteStackResult {
        val deleteStackResult = amazonCloudFormation.deleteStack(DeleteStackRequest().withStackName(stackName))
        val start = System.currentTimeMillis()

        loop@ while (true) {
            if (System.currentTimeMillis() > start + waitingLoopTimeout * 1000) throw RuntimeException("Timeout")
            try {
                amazonCloudFormation
                        .describeStacks(DescribeStacksRequest().withStackName(stackName))
                        .stacks
                        .first()
            } catch (e: AmazonServiceException) {
                if (e.message!!.contains("does not exist")) break@loop
            }
        }

        return deleteStackResult
    }

    private fun updateStack(stackTemplate: StackTemplate, capability: String): UpdateStackResult {
        val updateStackRequest = UpdateStackRequest()
                .withStackName(stackTemplate.name)
                .withParameters(stackTemplate.parameters)
                .withTags(stackTemplate.tags)
                .withCapabilities(listOf(capability))

        if (stackTemplate.templatePath.startsWith("http")) updateStackRequest.templateURL = stackTemplate.templatePath
        else updateStackRequest.templateBody = loadFile(stackTemplate.templatePath)

        return amazonCloudFormation.updateStack(updateStackRequest)
    }

    private fun createStack(stackTemplate: StackTemplate, capability: String, onCreationFailure: String): CreateStackResult {
        val createStackRequest = CreateStackRequest()
                .withStackName(stackTemplate.name)
                .withParameters(stackTemplate.parameters)
                .withTags(stackTemplate.tags)
                .withOnFailure(onCreationFailure)
                .withCapabilities(listOf(capability))

        if (stackTemplate.templatePath.startsWith("http")) createStackRequest.templateURL = stackTemplate.templatePath
        else createStackRequest.templateBody = loadFile(stackTemplate.templatePath)

        return amazonCloudFormation.createStack(createStackRequest)
    }

    private fun waitForStack(stackName: String, stackId: String, loopWait: Int) {
        val start = System.currentTimeMillis()

        loop@ while (true) {
            if (System.currentTimeMillis() > start + waitingLoopTimeout * 1000) throw RuntimeException("Timeout")

            try {
                val stack = amazonCloudFormation
                        .describeStacks(DescribeStacksRequest().withStackName(stackName))
                        .stacks
                        .first() ?: throw RuntimeException("The stack $stackName does not exist")

                val status = stack.stackStatus

                when {
                    successStackStatuses.contains(status) -> {
                        LOGGER.info("Status of stack $stackName is $status.")
                        printStackOutputs(stack)
                        break@loop
                    }
                    waitStackStatuses.contains(status) -> {
                        LOGGER.info("Status of stack $stackName is $status...")
                        Thread.sleep((loopWait * 1000).toLong())
                    }
                    else -> {
                        findFirstFailingResourceStatuses(stackName, stackId)
                        throw RuntimeException("Status of stack $stackName is $status. It seems to be failed!")
                    }
                }
            } catch (e: AmazonServiceException) {
                findFirstFailingResourceStatuses(stackName, stackId)
                throw RuntimeException("Waiting for stack $stackName failed!")
            }
        }

        findFirstFailingResourceStatuses(stackName, stackId)
    }

    private fun loadFile(templatePath: String): String {
        try {
            return FileUtils.readFileToString(File(templatePath))
        } catch (e: IOException) {
            throw RuntimeException("Could not read TemplateFile.", e)
        }
    }

    private fun findFirstFailingResourceStatuses(stackName: String, stackId: String) {
        val stackEvent = amazonCloudFormation
                .describeStackEvents(DescribeStackEventsRequest().withStackName(stackId))
                .stackEvents
                .sortedByDescending { it.timestamp }
                .takeWhile { it.resourceStatus != "UPDATE_IN_PROGRESS" }
                .lastOrNull { failingResourceStatuses.contains(it.resourceStatus) }

        if (stackEvent != null) throw RuntimeException(
                "Status of stack $stackName was ${stackEvent.resourceStatus}. Reason: ${stackEvent.resourceStatusReason}"
        )
    }

    private fun updateTerminationProtection(stackName: String, enableTerminationProtection: Boolean) {
        amazonCloudFormation.updateTerminationProtection(
                UpdateTerminationProtectionRequest()
                        .withStackName(stackName)
                        .withEnableTerminationProtection(enableTerminationProtection)
        )
        LOGGER.info("Update termination protection for stack: $stackName to $enableTerminationProtection")
    }

    companion object {
        private val successStackStatuses = listOf(
                "CREATE_COMPLETE",
                "UPDATE_COMPLETE",
                "DELETE_COMPLETE"
        )
        private val waitStackStatuses = listOf(
                "CREATE_IN_PROGRESS",
                "ROLLBACK_IN_PROGRESS",
                "DELETE_IN_PROGRESS",
                "UPDATE_IN_PROGRESS",
                "UPDATE_COMPLETE_CLEANUP_IN_PROGRESS",
                "UPDATE_ROLLBACK_IN_PROGRESS",
                "UPDATE_ROLLBACK_COMPLETE_CLEANUP_IN_PROGRESS"
        )
        private val stableStackStatuses = listOf(
                "CREATE_COMPLETE",
                "ROLLBACK_COMPLETE",
                "UPDATE_COMPLETE",
                "UPDATE_ROLLBACK_COMPLETE"
        )
        private val failingResourceStatuses = listOf(
                "CREATE_FAILED",
                "UPDATE_FAILED",
                "DELETE_FAILED"
        )
        private const val waitingLoopTimeout = 1800 // sec
        private val LOGGER = LogManager.getLogger(AmazonCloudformationClient::class.java)

        private fun printStackOutputs(stack: Stack) {
            LOGGER.info("==== Outputs ====")
            if (stack.outputs.size > 0) {
                stack.outputs.stream()
                        .forEach { o -> LOGGER.info("${o.outputKey} (${o.description}) = ${o.outputValue}") }
            } else {
                LOGGER.info("No outputs!")
            }
        }
    }
}
