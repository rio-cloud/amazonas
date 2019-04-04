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
import com.amazonaws.services.ecr.AmazonECR
import com.amazonaws.services.ecr.AmazonECRClientBuilder
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import org.apache.commons.codec.binary.Base64
import java.nio.charset.Charset

data class DockerCredentials(
        val username: String,
        val password: String
)

class EcrAuthorizationHelper(private val amazonECR: AmazonECR) {

    val dockerCredentials: DockerCredentials
        get() {
            val getAuthorizationTokenResult = amazonECR.getAuthorizationToken(GetAuthorizationTokenRequest())
            val decodedToken = Base64.decodeBase64(getAuthorizationTokenResult.authorizationData.first().authorizationToken)
            val credentials = String(decodedToken, Charset.forName("UTF-8"))
                    .split(":".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()

            return DockerCredentials(credentials[0], credentials[1])
        }

    constructor(credentialsProvider: AWSCredentialsProvider, region: String) :
            this(
                    AmazonECRClientBuilder
                            .standard()
                            .withRegion(region)
                            .withCredentials(credentialsProvider)
                            .build()
            )

}
