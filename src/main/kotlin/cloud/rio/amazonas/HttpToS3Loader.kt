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
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels

class HttpToS3Loader(private val client: AmazonS3) {

    constructor(credentialsProvider: AWSCredentialsProvider, region: String) : this(AmazonS3ClientBuilder
            .standard()
            .withRegion(region)
            .withCredentials(credentialsProvider)
            .build())

    fun loadIfNotPresent(url: String, md5: String, bucket: String, key: String) {
        if (isTargetPresent(md5, bucket, key)) {
            LOGGER.info("Object s3://$bucket/$key already there with matching hash, skipping.")
        } else {
            LOGGER.info("Object s3://$bucket/$key not found or hash does not match.")

            val localPath = File("./$key")
            createParentFolder(localPath)
            downloadFromUrl(url, localPath)
            uploadToS3(localPath, bucket, key)
            deleteLocalFile(localPath)
        }
    }

    private fun isTargetPresent(md5: String, bucket: String, key: String): Boolean {
        return if (!client.doesObjectExist(bucket, key)) {
            false
        } else {
            client.getObjectMetadata(bucket, key).eTag == md5
        }
    }

    private fun createParentFolder(localPath: File) {
        val localFolder = File(localPath.parent)
        if (!localFolder.exists()) {
            if (localFolder.mkdirs()) {
                LOGGER.info("Created directory ${localFolder.absolutePath}")
            }
        }
    }

    private fun downloadFromUrl(url: String, localFile: File) {
        LOGGER.info("Downloading from URL $url...")
        val website = URL(url)
        val rbc = Channels.newChannel(website.openStream())
        val fos = FileOutputStream(localFile)
        fos.channel.transferFrom(rbc, 0, java.lang.Long.MAX_VALUE)
    }

    private fun uploadToS3(localPath: File, bucket: String, key: String) {
        LOGGER.info("Uploading $key to s3://$bucket...")
        val request = PutObjectRequest(bucket, key, localPath)
        val metadata = ObjectMetadata()
        metadata.sseAlgorithm = ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION
        metadata.cacheControl = "no-cache, no-store"
        request.metadata = metadata
        client.putObject(request)
    }

    private fun deleteLocalFile(localPath: File) {
        if (localPath.delete()) {
            LOGGER.info("Deleted $localPath")
        } else {
            LOGGER.warn("Deletion of $localPath failed.")
        }
    }

    companion object {
        private val LOGGER = LogManager.getLogger(HttpToS3Loader::class.java)
    }
}
