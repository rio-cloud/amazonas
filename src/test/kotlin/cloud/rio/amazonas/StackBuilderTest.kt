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


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("A StackTemplate")
class StackBuilderTest {

    private val configFilePath = "src/test/resources/cloudformation/deploy-library-test-stack.template.yaml"
    private val stackName = "TestStack"

    @Test
    fun `should be created without parameters or tags`() {
        val stack = stack(stackName, configFilePath)

        assertEquals(stackName, stack.name)
        assertEquals(configFilePath, stack.templatePath)
        assertEquals(0, stack.parameters.size)
        assertEquals(0, stack.tags.size)
    }

    @Test
    fun `should be created with one parameter`() {
        val stack = stack(stackName, configFilePath) {
            parameters {
                put("ServerCertificateArn", "test_server_arn_new")
            }
        }

        assertEquals(1, stack.parameters.size)
        assertEquals("ServerCertificateArn", stack.parameters[0].parameterKey)
        assertEquals("test_server_arn_new", stack.parameters[0].parameterValue)
    }

    @Test
    fun `should be created with a map of parameters`() {
        val parameterMap = mapOf("ServerCertificateArn" to "test_server_arn_new", "AccountId" to "29741983098")
        val stack = stack(stackName, configFilePath) {
            parameters {
                putAll(parameterMap)
            }
        }

        assertEquals(2, stack.parameters.size)
        assertTrue(listOf("ServerCertificateArn", "AccountId").contains(stack.parameters[0].parameterKey))
        assertTrue(listOf("ServerCertificateArn", "AccountId").contains(stack.parameters[1].parameterKey))
        assertTrue(listOf("test_server_arn_new", "29741983098").contains(stack.parameters[0].parameterValue))
        assertTrue(listOf("test_server_arn_new", "29741983098").contains(stack.parameters[1].parameterValue))
    }

    @Test
    fun `should have each parameter only once`() {
        val parameterMap = mapOf("ServerCertificateArn" to "test_server_arn_new", "AccountId" to "29741983098")
        val stack = stack(stackName, configFilePath) {
            parameters {
                putAll(parameterMap)
                put("AccountId", "1234")
                put("AccountId", "5678")
            }
        }

        assertEquals(2, stack.parameters.size)
        assertTrue(listOf("ServerCertificateArn", "AccountId").contains(stack.parameters[0].parameterKey))
        assertTrue(listOf("ServerCertificateArn", "AccountId").contains(stack.parameters[1].parameterKey))
        assertTrue(listOf("test_server_arn_new", "5678").contains(stack.parameters[0].parameterValue))
        assertTrue(listOf("test_server_arn_new", "5678").contains(stack.parameters[1].parameterValue))
    }

    @Test
    fun `should be created with one tag`() {
        val stack = stack(stackName, configFilePath) {
            tags {
                put("ServerCertificateArn", "test_server_arn_new")
            }
        }
        assertEquals(1, stack.tags.size)
        assertEquals("ServerCertificateArn", stack.tags[0].key)
        assertEquals("test_server_arn_new", stack.tags[0].value)
    }

    @Test
    fun `should be created with a map of tags`() {
        val tagMap = mapOf("ServerCertificateArn" to "test_server_arn_new", "AccountId" to "29741983098")
        val stack = stack(stackName, configFilePath){
            tags {
                putAll(tagMap)
            }
        }

        assertEquals(2, stack.tags.size)
        assertTrue(listOf("ServerCertificateArn", "AccountId").contains(stack.tags[0].key))
        assertTrue(listOf("ServerCertificateArn", "AccountId").contains(stack.tags[1].key))
        assertTrue(listOf("test_server_arn_new", "29741983098").contains(stack.tags[0].value))
        assertTrue(listOf("test_server_arn_new", "29741983098").contains(stack.tags[1].value))
    }

    @Test
    fun `should have each tag only once`() {
        val tagMap = mapOf("ServerCertificateArn" to "test_server_arn_new", "AccountId" to "29741983098")
        val stack = stack(stackName, configFilePath) {
            tags {
                putAll(tagMap)
                put("AccountId", "1234")
                put("AccountId", "5678")
            }
        }

        assertEquals(2, stack.tags.size)
        assertTrue(listOf("ServerCertificateArn", "AccountId").contains(stack.tags[0].key))
        assertTrue(listOf("ServerCertificateArn", "AccountId").contains(stack.tags[1].key))
        assertTrue(listOf("test_server_arn_new", "5678").contains(stack.tags[0].value))
        assertTrue(listOf("test_server_arn_new", "5678").contains(stack.tags[1].value))
    }
}
