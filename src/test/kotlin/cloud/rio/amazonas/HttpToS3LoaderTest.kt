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

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import io.findify.s3mock.S3Mock
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("HttpToS3Loader.loadIfNotPresent")
class HttpToS3LoaderTest {
    private val md5 = "d8e8fca2dc0f896fd7cb4cb0031ba249"
    private val bucket = "test-bucket"
    private val key = "prefix/key"
    private val url = "file://${System.getProperty("user.dir")}/src/test/resources/testfile"
    private val testfileContent = File("${System.getProperty("user.dir")}/src/test/resources/testfile").readText()
    private val s3Mock = S3Mock.Builder().withPort(8001).withInMemoryBackend().build()
    private lateinit var amazonS3: AmazonS3

    @BeforeAll
    fun setUp() {
        s3Mock.start()

        val endpoint = AwsClientBuilder.EndpointConfiguration("http://localhost:8001", "eu-west-1")
        amazonS3 = AmazonS3ClientBuilder
                .standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(endpoint)
                .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
                .build()
    }

    @AfterAll
    fun tearDown() {
        s3Mock.shutdown()
    }

    @BeforeEach
    fun createBucket() {
        amazonS3.createBucket(bucket)
    }


    @AfterEach
    fun deleteBucket() {
        amazonS3.deleteBucket(bucket)
    }

    @Test
    fun `should load the file if it is not present`() {
        val loader = HttpToS3Loader(amazonS3)

        loader.loadIfNotPresent(url, md5, bucket, key)

        Assertions.assertTrue(amazonS3.doesObjectExist(bucket, key))
        val s3Object = amazonS3.getObject(bucket, key)
        assertEquals("AES256", s3Object.objectMetadata.sseAlgorithm)
        assertEquals(testfileContent, s3Object.objectContent.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `should load the file if a file with not matching hash is present`() {
        amazonS3.putObject(bucket, key, "Simulate existing file with same key")
        val loader = HttpToS3Loader(amazonS3)

        loader.loadIfNotPresent(url, md5, bucket, key)

        Assertions.assertTrue(amazonS3.doesObjectExist(bucket, key))
        val s3Object = amazonS3.getObject(bucket, key)
        assertEquals("AES256", s3Object.objectMetadata.sseAlgorithm)
        assertEquals(testfileContent, s3Object.objectContent.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `should not load the file if a file with matching hash is present`() {
        amazonS3.putObject(bucket, key, testfileContent)
        val lastModifiedExpected = amazonS3.getObject(bucket, key).objectMetadata.lastModified
        Thread.sleep(1000)
        val loader = HttpToS3Loader(amazonS3)

        loader.loadIfNotPresent(url, md5, bucket, key)
        val lastModifiedActual = amazonS3.getObject(bucket, key).objectMetadata.lastModified
        assertEquals(lastModifiedExpected, lastModifiedActual)
    }
}
