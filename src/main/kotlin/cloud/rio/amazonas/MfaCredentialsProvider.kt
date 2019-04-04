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
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.profile.ProfilesConfigFile
import com.amazonaws.auth.profile.internal.BasicProfile
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.services.securitytoken.model.Credentials
import com.google.gson.Gson
import java.io.*
import java.util.*
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

class MfaCredentialsProvider(
        profileName: String,
        private val guiEnabled: Boolean = true,
        private val sessionLengthSeconds: String = "3600"
) : AWSCredentialsProvider {
    private val profile: BasicProfile = ProfilesConfigFile().allBasicProfiles[profileName]
            ?: throw IllegalArgumentException(
                    "cannot initialize MFA Provider. No profileName named $profileName" +
                            " found on your machine. Please check your .aws/credentials file"
            )
    private var cachedCredentials: BasicSessionCredentials? = null
    private var cacheExpiration: Date = Date()
    private val cacheFileName: String = "/tmp/aws-session-credentials-$profileName"

    override fun getCredentials(): BasicSessionCredentials {
        if (Date().before(cacheExpiration)) {
            return cachedCredentials ?: updateCachedCredentials()
        }
        return updateCachedCredentials()
    }

    private fun updateCachedCredentials(): BasicSessionCredentials {
        val credentials = readOrRenewCredentials()
        val returnCredentials = BasicSessionCredentials(
                credentials.accessKeyId,
                credentials.secretAccessKey,
                credentials.sessionToken
        )
        cachedCredentials = returnCredentials
        cacheExpiration = credentials.expiration

        return returnCredentials
    }

    private fun readOrRenewCredentials(): Credentials {
        var credentials = readPersistedCredentials()
        if (credentials == null || Date().after(credentials.expiration)) {
            credentials = requestFreshCredentials()
            persistCredentials(credentials)
        }
        return credentials
    }

    private fun requestFreshCredentials(): Credentials {
        val roleArn = profile.roleArn
        val mfaSerial = profile.getPropertyValue("mfa_serial")
        val sourceProfileCredentials = ProfileCredentialsProvider(profile.roleSourceProfile).credentials
        val mfaCode = promptForMfaCode()

        val sts = AWSSecurityTokenServiceClientBuilder
                .standard()
                .withRegion(profile.region)
                .withCredentials(
                        AWSStaticCredentialsProvider(
                                sourceProfileCredentials
                        )
                )
                .build()

        val roleRequest = AssumeRoleRequest()
                .withTokenCode(mfaCode)
                .withDurationSeconds(Integer.parseInt(sessionLengthSeconds))
                .withSerialNumber(mfaSerial)
                .withRoleArn(roleArn)
                .withRoleSessionName(UUID.randomUUID().toString())

        val roleResult = sts.assumeRole(roleRequest)

        return roleResult.credentials
    }

    private fun readPersistedCredentials(): Credentials? {
        val f = File(cacheFileName)
        if (f.exists() && !f.isDirectory) {
            val credentials: Credentials
            try {
                val reader = BufferedReader(FileReader(cacheFileName))
                val json = reader.readLine()
                reader.close()
                credentials = Gson().fromJson(json, Credentials::class.java)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }

            return credentials
        } else {
            return null
        }
    }

    private fun persistCredentials(credentials: Credentials) {
        val gson = Gson()
        val json = gson.toJson(credentials)
        try {
            val writer = BufferedWriter(FileWriter(cacheFileName))
            writer.write(json)
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun promptForMfaCode(): String {
        val mfaCode = if (guiEnabled) {
            requestMfaInputWithGui()
        } else {
            val `in` = Scanner(System.`in`)
            print("Please enter MFA Code: ")
            `in`.nextLine()
        }
        if (mfaCode.isEmpty() || mfaCode.length < 6) {
            throw InputMismatchException("You must enter a valid MFA Code!")
        }
        return mfaCode
    }

    private fun requestMfaInputWithGui(): String {
        val panel = JPanel()
        val mfaField = JTextField(10)
        panel.add(JLabel("Please enter MFA Code: "))
        panel.add(mfaField)
        val pane = object : JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION) {
            override fun selectInitialValue() {
                mfaField.requestFocusInWindow()
            }
        }
        val d = pane.createDialog("MFA Input")
        d.isAlwaysOnTop = true
        d.isVisible = true
        return mfaField.text
    }

    override fun refresh() {
        throw UnsupportedOperationException("refresh method is not implemented")
    }
}