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

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.*
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("AmazonCloudformationClient.createOrUpdateStackAndWait")
internal class AmazonCloudformationClientTest {

    private val amazonCloudFormationMock = mockkClass(AmazonCloudFormation::class)
    private val describeStackResultMock = mockkClass(DescribeStacksResult::class)
    private val createStackResultMock = mockkClass(CreateStackResult::class)
    private val deleteStackResultMock = mockkClass(DeleteStackResult::class)
    private val updateStackResultMock = mockkClass(UpdateStackResult::class)
    private val stackEventMock = mockkClass(StackEvent::class)
    private val stackMock = mockkClass(com.amazonaws.services.cloudformation.model.Stack::class)
    private val updateTerminationProtectionResultMock = mockkClass((UpdateTerminationProtectionResult::class))
    private val stackId = "stack_id"
    private val stackTemplate = StackTemplate("TestStack", "https://Path.to.stack")

    @BeforeEach
    fun prepareMocks() {
        every { stackMock.stackId } returns stackId
        every { stackMock.outputs } returns listOf()
        every { createStackResultMock.stackId } returns stackId
        every { updateStackResultMock.stackId } returns stackId
        every { describeStackResultMock.stacks } returns listOf(stackMock)
        every { stackEventMock.resourceStatusReason } returns "Hello World"
        every { amazonCloudFormationMock.describeStacks(DescribeStacksRequest().withStackName(stackTemplate.name)) } returns describeStackResultMock
        every { amazonCloudFormationMock.describeStackEvents(DescribeStackEventsRequest().withStackName("stack_id")).stackEvents } returns listOf(stackEventMock)
        every { amazonCloudFormationMock.deleteStack(DeleteStackRequest().withStackName(stackTemplate.name)) } returns deleteStackResultMock
        every {
            amazonCloudFormationMock.updateTerminationProtection(UpdateTerminationProtectionRequest()
                    .withStackName(stackTemplate.name)
                    .withEnableTerminationProtection(false))
        } returns updateTerminationProtectionResultMock
        every {
            amazonCloudFormationMock.createStack(CreateStackRequest()
                    .withStackName(stackTemplate.name)
                    .withParameters(stackTemplate.parameters)
                    .withTags(stackTemplate.tags)
                    .withOnFailure("DELETE")
                    .withCapabilities(Capability.CAPABILITY_NAMED_IAM)
                    .withTemplateURL(stackTemplate.templatePath))
        } returns createStackResultMock
        every {
            amazonCloudFormationMock.updateStack(UpdateStackRequest()
                    .withStackName(stackTemplate.name)
                    .withParameters(stackTemplate.parameters)
                    .withTags(stackTemplate.tags)
                    .withCapabilities(Capability.CAPABILITY_NAMED_IAM)
                    .withTemplateURL(stackTemplate.templatePath))
        } returns updateStackResultMock
    }

    @AfterEach
    fun resetMocks() {
        clearAllMocks()
    }

    @Test
    fun `should create a stack if it does not exits`() {
        every { stackMock.stackStatus } returnsMany listOf("CREATE_IN_PROGRESS", "CREATE_COMPLETE")
        every { stackEventMock.resourceStatus } returnsMany listOf("CREATE_COMPLETE")
        every {
            amazonCloudFormationMock.describeStacks(DescribeStacksRequest().withStackName(stackTemplate.name))
        } throws AmazonServiceException("This stack does not exist!") andThen describeStackResultMock

        val amazonCloudformationClient = AmazonCloudformationClient(amazonCloudFormationMock)
        amazonCloudformationClient.createOrUpdateStackAndWait(stackTemplate, sleepWhileWaitingInSec = 1)

        verify {
            amazonCloudFormationMock.createStack(CreateStackRequest()
                    .withStackName(stackTemplate.name)
                    .withParameters(stackTemplate.parameters)
                    .withTags(stackTemplate.tags)
                    .withOnFailure("DELETE")
                    .withCapabilities(Capability.CAPABILITY_NAMED_IAM)
                    .withTemplateURL(stackTemplate.templatePath))
        }
    }

    @Test
    fun `should first delete and then create a stack if it has the status DELETE_COMPLETE`() {
        every { stackMock.stackStatus } returnsMany listOf("DELETE_COMPLETE", "UPDATE_IN_PROGRESS", "CREATE_COMPLETE")
        every { stackEventMock.resourceStatus } returnsMany listOf("CREATE_COMPLETE")
        every {
            amazonCloudFormationMock.describeStacks(DescribeStacksRequest().withStackName(stackTemplate.name))
        } returns describeStackResultMock andThenThrows AmazonServiceException("This stack does not exist!") andThen describeStackResultMock

        val amazonCloudformationClient = AmazonCloudformationClient(amazonCloudFormationMock)
        amazonCloudformationClient.createOrUpdateStackAndWait(stackTemplate, sleepWhileWaitingInSec = 1)

        verify {
            amazonCloudFormationMock.deleteStack(DeleteStackRequest().withStackName(stackTemplate.name))
            amazonCloudFormationMock.createStack(CreateStackRequest()
                    .withStackName(stackTemplate.name)
                    .withParameters(stackTemplate.parameters)
                    .withTags(stackTemplate.tags)
                    .withOnFailure("DELETE")
                    .withCapabilities(Capability.CAPABILITY_NAMED_IAM)
                    .withTemplateURL(stackTemplate.templatePath))
        }
    }

    @Test
    fun `should do nothing if the stack cannot be updated`() {
        every {
            amazonCloudFormationMock.describeStacks(DescribeStacksRequest().withStackName(stackTemplate.name))
        } throws AmazonServiceException("No updates are to be performed.")

        val amazonCloudformationClient = AmazonCloudformationClient(amazonCloudFormationMock)
        amazonCloudformationClient.createOrUpdateStackAndWait(stackTemplate, sleepWhileWaitingInSec = 1)

        verifyAll {
            amazonCloudFormationMock.describeStacks(DescribeStacksRequest().withStackName(stackTemplate.name))
        }
    }

    @Test
    fun `should update a stack if it has the status CREATE_COMPLETE`() {
        every { stackMock.stackStatus } returnsMany listOf("CREATE_COMPLETE", "CREATE_COMPLETE", "UPDATE_IN_PROGRESS", "UPDATE_COMPLETE")
        every { stackEventMock.resourceStatus } returnsMany listOf("CREATE_COMPLETE")

        val amazonCloudformationClient = AmazonCloudformationClient(amazonCloudFormationMock)
        amazonCloudformationClient.createOrUpdateStackAndWait(stackTemplate, sleepWhileWaitingInSec = 1)

        verify {
            amazonCloudFormationMock.updateStack(UpdateStackRequest()
                    .withStackName(stackTemplate.name)
                    .withParameters(stackTemplate.parameters)
                    .withTags(stackTemplate.tags)
                    .withCapabilities(Capability.CAPABILITY_NAMED_IAM)
                    .withTemplateURL(stackTemplate.templatePath))
        }
    }

    @Test
    fun `should print the failure reason in case the stack has the status UPDATE_FAILED`() {
        val stackEventUpdateInProgress = StackEvent().withResourceStatus("UPDATE_IN_PROGRESS").withTimestamp(Date(3L))
        val stackEventUpdateComplete = StackEvent().withResourceStatus("UPDATE_COMPLETE").withTimestamp(Date(4L))
        val stackEventUpdateFailed1 = StackEvent().withResourceStatus("UPDATE_FAILED").withResourceStatusReason("Reason 1").withTimestamp(Date(5L))
        val stackEventUpdateFailed2 = StackEvent().withResourceStatus("UPDATE_FAILED").withResourceStatusReason("Reason 2").withTimestamp(Date(6L))
        val stackEventUpdateFailed3 = StackEvent().withResourceStatus("UPDATE_FAILED").withResourceStatusReason("Reason 3").withTimestamp(Date(7L))
        every { stackMock.stackStatus } returnsMany listOf("CREATE_COMPLETE", "CREATE_COMPLETE", "UPDATE_IN_PROGRESS", "DELETE_IN_PROGRESS", "ROLLBACK_IN_PROGRESS", "UPDATE_ROLLBACK_COMPLETE")
        every {
            amazonCloudFormationMock.describeStackEvents(DescribeStackEventsRequest().withStackName("stack_id")).stackEvents
        } returns listOf(stackEventUpdateFailed3, stackEventUpdateInProgress, stackEventUpdateFailed1, stackEventUpdateFailed2, stackEventUpdateComplete)

        val amazonCloudformationClient = AmazonCloudformationClient(amazonCloudFormationMock)
        val codeToTest: () -> Unit = { amazonCloudformationClient.createOrUpdateStackAndWait(stackTemplate, sleepWhileWaitingInSec = 1) }

        val exception = assertThrows(RuntimeException::class.java, codeToTest)
        assertEquals("Status of stack ${stackTemplate.name} was UPDATE_FAILED. Reason: ${stackEventUpdateFailed1.resourceStatusReason}", exception.message)
        verify {
            amazonCloudFormationMock.updateStack(UpdateStackRequest()
                    .withStackName(stackTemplate.name)
                    .withParameters(stackTemplate.parameters)
                    .withTags(stackTemplate.tags)
                    .withCapabilities(Capability.CAPABILITY_NAMED_IAM)
                    .withTemplateURL(stackTemplate.templatePath))
        }
    }

    @Test
    fun `should print the failure reason in case the stack has the status CREATE_FAILED`() {
        val stackEventCreateFailed1 = StackEvent().withResourceStatus("CREATE_FAILED").withResourceStatusReason("Reason 1").withTimestamp(Date(5L))
        val stackEventCreateFailed2 = StackEvent().withResourceStatus("CREATE_FAILED").withResourceStatusReason("Reason 2").withTimestamp(Date(6L))
        val stackEventCreateInProgress = StackEvent().withResourceStatus("CREATE_IN_PROGRESS").withTimestamp(Date(4L))
        every { stackMock.stackStatus } returnsMany listOf("CREATE_IN_PROGRESS", "DELETE_IN_PROGRESS", "DELETE_COMPLETE")
        every {
            amazonCloudFormationMock.describeStacks(DescribeStacksRequest().withStackName(stackTemplate.name))
        } throws AmazonServiceException("This stack does not exist!") andThen describeStackResultMock
        every {
            amazonCloudFormationMock.describeStackEvents(DescribeStackEventsRequest().withStackName("stack_id")).stackEvents
        } returns listOf(stackEventCreateFailed1, stackEventCreateFailed2, stackEventCreateInProgress)

        val amazonCloudformationClient = AmazonCloudformationClient(amazonCloudFormationMock)
        val codeToTest: () -> Unit = { amazonCloudformationClient.createOrUpdateStackAndWait(stackTemplate, sleepWhileWaitingInSec = 1) }

        val exception = assertThrows(RuntimeException::class.java, codeToTest)
        assertEquals("Status of stack ${stackTemplate.name} was CREATE_FAILED. Reason: ${stackEventCreateFailed1.resourceStatusReason}", exception.message)
        verify {
            amazonCloudFormationMock.createStack(CreateStackRequest()
                    .withStackName(stackTemplate.name)
                    .withParameters(stackTemplate.parameters)
                    .withTags(stackTemplate.tags)
                    .withOnFailure("DELETE")
                    .withCapabilities(Capability.CAPABILITY_NAMED_IAM)
                    .withTemplateURL(stackTemplate.templatePath))
        }
    }

    @Test
    fun `should create a stack with termination protection enabled`() {
        every { stackMock.stackStatus } returnsMany listOf("CREATE_IN_PROGRESS", "CREATE_COMPLETE")
        every { stackEventMock.resourceStatus } returnsMany listOf("CREATE_COMPLETE")
        every {
            amazonCloudFormationMock.describeStacks(DescribeStacksRequest().withStackName(stackTemplate.name))
        } throws AmazonServiceException("This stack does not exist!") andThen describeStackResultMock
        every {
            amazonCloudFormationMock.updateTerminationProtection(UpdateTerminationProtectionRequest()
                    .withStackName(stackTemplate.name)
                    .withEnableTerminationProtection(true))
        } returns updateTerminationProtectionResultMock

        val amazonCloudformationClient = AmazonCloudformationClient(amazonCloudFormationMock)
        amazonCloudformationClient.createOrUpdateStackAndWait(stackTemplate, sleepWhileWaitingInSec = 1, enableTerminationProtection = true)

        verify {
            amazonCloudFormationMock.createStack(CreateStackRequest()
                    .withStackName(stackTemplate.name)
                    .withParameters(stackTemplate.parameters)
                    .withTags(stackTemplate.tags)
                    .withOnFailure("DELETE")
                    .withCapabilities(Capability.CAPABILITY_NAMED_IAM)
                    .withTemplateURL(stackTemplate.templatePath))
        }
        verify {
            amazonCloudFormationMock.updateTerminationProtection(UpdateTerminationProtectionRequest()
                    .withStackName(stackTemplate.name)
                    .withEnableTerminationProtection(true))
        }
    }

}