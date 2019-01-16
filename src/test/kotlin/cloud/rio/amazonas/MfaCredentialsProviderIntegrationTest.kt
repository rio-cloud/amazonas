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

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class MfaCredentialsProviderIntegrationTest {
    @Disabled // Only for local testing with valid AWS access keys (for profile "dev"), thus disabled.
    @Test
    fun getCredentials() {
        val credentialsProvider = MfaCredentialsProvider("dev")
        credentialsProvider.credentials

        val amazonS3 = AmazonS3ClientBuilder.standard()
                .withRegion("eu-west-1")
                .withCredentials(credentialsProvider)
                .build()
        val buckets = amazonS3.listBuckets()
        buckets.forEach { bucket -> println(bucket.name) }
    }
}

