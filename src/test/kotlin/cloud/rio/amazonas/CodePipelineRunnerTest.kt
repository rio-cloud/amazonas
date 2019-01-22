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

import com.amazonaws.services.codepipeline.AWSCodePipeline
import com.amazonaws.services.codepipeline.model.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.verify
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CodepipelineRunner.startPipelineAndWaitForSuccess")
internal class CodePipelineRunnerTest {

    private val awsCodePipelineMock = mockkClass(AWSCodePipeline::class)
    private val startPipelineExecutionResultMock = mockkClass(StartPipelineExecutionResult::class)
    private val getPipelineExecutionResultMock = mockkClass(GetPipelineExecutionResult::class)
    private val pipelineExecutionId = "id"
    private val pipelineName = "pipeline"
    private val waitingStatus = "InProgress"
    private val successStatus = "Succeeded"
    private val failedStatus = "Failed"

    @BeforeEach
    fun prepareMocks() {
        every { awsCodePipelineMock.startPipelineExecution(StartPipelineExecutionRequest().withName(pipelineName)) } returns startPipelineExecutionResultMock
        every { startPipelineExecutionResultMock.pipelineExecutionId } returns pipelineExecutionId
    }

    @AfterEach
    fun resetMocks() {
        clearAllMocks()
    }


    @Test
    fun `start a codepipeline, which runs successfully`() {
        every { getPipelineExecutionResultMock.pipelineExecution.status } returnsMany listOf(waitingStatus, successStatus)
        every { awsCodePipelineMock.getPipelineExecution(GetPipelineExecutionRequest().withPipelineName(pipelineName).withPipelineExecutionId(pipelineExecutionId)) } returns getPipelineExecutionResultMock

        val codePipelineRunner = CodePipelineRunner(awsCodePipelineMock)
        codePipelineRunner.startPipelineAndWaitForSuccess(pipelineName, 1)

        verify(exactly = 1) {
            awsCodePipelineMock.startPipelineExecution(StartPipelineExecutionRequest()
                    .withName(pipelineName))
        }
        verify(exactly = 2) {
            awsCodePipelineMock.getPipelineExecution(GetPipelineExecutionRequest()
                    .withPipelineName(pipelineName)
                    .withPipelineExecutionId(pipelineExecutionId))
        }
    }

    @Test
    fun `start a codepipeline, that does not run successfully and throw exception`() {
        every { getPipelineExecutionResultMock.pipelineExecution.status } returnsMany listOf(waitingStatus, failedStatus)
        every { awsCodePipelineMock.getPipelineExecution(GetPipelineExecutionRequest().withPipelineName(pipelineName).withPipelineExecutionId(pipelineExecutionId)) } returns getPipelineExecutionResultMock

        val codePipelineRunner = CodePipelineRunner(awsCodePipelineMock)
        val codeToTest: () -> Unit = { codePipelineRunner.startPipelineAndWaitForSuccess(pipelineName, 1) }

        val exception = assertThrows(PipelineFailedException::class.java, codeToTest)
        assertEquals("Pipeline $pipelineName has final status $failedStatus", exception.message)
        verify(exactly = 1) {
            awsCodePipelineMock.startPipelineExecution(StartPipelineExecutionRequest()
                    .withName(pipelineName))
        }
        verify(exactly = 2) {
            awsCodePipelineMock.getPipelineExecution(GetPipelineExecutionRequest()
                    .withPipelineName(pipelineName)
                    .withPipelineExecutionId(pipelineExecutionId))
        }
    }

    @Test
    fun `start a codepipeline, that does need to retry due to not finding pipeline execution on first try`() {
        every { getPipelineExecutionResultMock.pipelineExecution.status } returnsMany listOf(successStatus)
        every {
            awsCodePipelineMock.getPipelineExecution(GetPipelineExecutionRequest().withPipelineName(pipelineName).withPipelineExecutionId(pipelineExecutionId))
        } throws PipelineExecutionNotFoundException("Pipeline Execution with id $pipelineExecutionId does not exist in pipeline with name $pipelineName") andThen getPipelineExecutionResultMock

        val codePipelineRunner = CodePipelineRunner(awsCodePipelineMock)
        codePipelineRunner.startPipelineAndWaitForSuccess(pipelineName, 1)

        verify(exactly = 1) {
            awsCodePipelineMock.startPipelineExecution(StartPipelineExecutionRequest()
                    .withName(pipelineName))
        }
        verify(exactly = 2) {
            awsCodePipelineMock.getPipelineExecution(GetPipelineExecutionRequest()
                    .withPipelineName(pipelineName)
                    .withPipelineExecutionId(pipelineExecutionId))
        }
    }
}