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

import com.amazonaws.services.kms.AWSKMS
import com.amazonaws.services.kms.model.AliasListEntry
import com.amazonaws.services.kms.model.ListAliasesResult
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult
import com.amazonaws.services.simplesystemsmanagement.model.Parameter
import com.amazonaws.services.simplesystemsmanagement.model.PutParameterRequest
import com.amazonaws.services.simplesystemsmanagement.model.PutParameterResult
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("SsmParameterCopier.copy() should")
internal class SsmParameterCopierTest {

    private val sourceSsm = mockkClass(AWSSimpleSystemsManagement::class)
    private val targetSsm = mockkClass(AWSSimpleSystemsManagement::class)
    private val kmsClient = mockkClass(AWSKMS::class)

    @AfterEach
    fun resetMocks() {
        clearAllMocks()
    }

    @Test
    @DisplayName("copy a parameter from the source to the target parameter store")
    fun copyParameter() {
        val sourceKey = "source-parameter"
        val sourceValue = "value"
        val targetKey = "target-parameter"
        every {
            sourceSsm.getParameter(match { it.name == sourceKey })
        } returns
                GetParameterResult().withParameter(Parameter().withName(sourceKey).withValue(sourceValue))
        every {
            targetSsm.putParameter(match { it.name == targetKey })
        } returns
                PutParameterResult().withVersion(1L)

        val copier = SsmParameterCopier(sourceSsm, targetSsm, kmsClient)
        copier.copyParameter(sourceKey, targetKey)

        val slot = slot<PutParameterRequest>()
        verify { targetSsm.putParameter(capture(slot)) }
        val actualRequest = slot.captured
        assertEquals(targetKey, actualRequest.name)
        assertEquals(sourceValue, actualRequest.value)
    }

    @Test
    @DisplayName("if a key alias is specified, encrypt the target parameter with the corresponding key")
    fun copyParameterWithEncryption() {
        val sourceKey = "source-parameter"
        val sourceValue = "value"
        val targetKey = "target-parameter"
        val keyName = "the-key"
        val targetKeyId = "right-key"
        every {
            sourceSsm.getParameter(match { it.name == sourceKey && it.isWithDecryption!! })
        } returns
                GetParameterResult().withParameter(Parameter().withName(sourceKey).withValue(sourceValue))
        every {
            targetSsm.putParameter(match { it.name == targetKey })
        } returns
                PutParameterResult().withVersion(1L)
        every {
            kmsClient.listAliases(any())
        } returns
                ListAliasesResult()
                        .withAliases(
                                AliasListEntry().withAliasName("alias/another-key").withTargetKeyId("wrong-key"),
                                AliasListEntry().withAliasName("alias/the-key").withTargetKeyId(targetKeyId)
                        )

        val copier = SsmParameterCopier(sourceSsm, targetSsm, kmsClient)
        copier.copyParameter(sourceKey, targetKey, encryptionKeyName = keyName)

        val slot = slot<PutParameterRequest>()
        verify { targetSsm.putParameter(capture(slot)) }
        val actualRequest = slot.captured
        assertEquals(targetKey, actualRequest.name)
        assertEquals(sourceValue, actualRequest.value)
        assertEquals(targetKeyId, actualRequest.keyId)
    }
}
