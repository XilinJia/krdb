package io.github.xilinjia.krdb.test.mongodb.common

import io.github.xilinjia.krdb.internal.platform.runBlocking
import io.github.xilinjia.krdb.mongodb.Credentials
import io.github.xilinjia.krdb.mongodb.auth.EmailPasswordAuth
import io.github.xilinjia.krdb.mongodb.exceptions.AppException
import io.github.xilinjia.krdb.mongodb.exceptions.AuthException
import io.github.xilinjia.krdb.mongodb.exceptions.BadRequestException
import io.github.xilinjia.krdb.mongodb.exceptions.ServiceException
import io.github.xilinjia.krdb.mongodb.exceptions.UserAlreadyConfirmedException
import io.github.xilinjia.krdb.mongodb.exceptions.UserAlreadyExistsException
import io.github.xilinjia.krdb.mongodb.exceptions.UserNotFoundException
import io.github.xilinjia.krdb.test.mongodb.TestApp
import io.github.xilinjia.krdb.test.mongodb.asTestApp
import io.github.xilinjia.krdb.test.mongodb.syncServerAppName
import io.github.xilinjia.krdb.test.mongodb.util.BaasApp
import io.github.xilinjia.krdb.test.mongodb.util.BaseAppInitializer
import io.github.xilinjia.krdb.test.mongodb.util.DefaultPartitionBasedAppInitializer
import io.github.xilinjia.krdb.test.mongodb.util.addEmailProvider
import io.github.xilinjia.krdb.test.util.TestHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class EmailPasswordAuthWithAutoConfirmTests {
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultPartitionBasedAppInitializer)
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun registerUser() = runBlocking {
        val (email, password) = TestHelper.randomEmail() to "password1234"
        app.emailPasswordAuth.registerUser(email, password)
        val user = app.login(Credentials.emailPassword(email, password))
        assertNotNull(user)
        Unit
    }

    @Test
    fun registerUser_sameUserThrows() = runBlocking {
        val (email, password) = TestHelper.randomEmail() to "password1234"
        app.emailPasswordAuth.registerUser(email, password)
        assertFailsWith<UserAlreadyExistsException> {
            app.emailPasswordAuth.registerUser(email, password)
        }
        Unit
    }

    @Test
    fun registerUser_invalidServerArgsThrows_invalidUser() = runBlocking {
        // Invalid mail and too short password
        val (email, password) = "invalid-email" to "1234"
        // TODO do exhaustive exception assertion once we have all AppException fields in place
        assertFailsWith<AppException> {
            app.emailPasswordAuth.registerUser(email, password)
        }
        Unit
    }

    @Test
    fun registerUser_invalidServerArgsThrows_invalidPassword() {
        runBlocking {
            // Valid mail but too short password
            val (email, password) = TestHelper.randomEmail() to "1234"
            // TODO do exhaustive exception assertion once we have all AppException fields in place
            assertFailsWith<AppException> {
                app.emailPasswordAuth.registerUser(email, password)
            }
        }
    }

    @Ignore
    @Test
    fun confirmUser() {
        TODO("Figure out how to manually test this")
    }

    @Ignore
    @Test
    fun confirmUser_alreadyConfirmedThrows() {
        TODO("Figure out how to manually test this")
    }

    @Test
    fun confirmUser_invalidServerArgsThrows() {
        val provider = app.emailPasswordAuth
        runBlocking {
            // TODO Do better validation when AppException is done
            //  assertEquals(ErrorCode.BAD_REQUEST, ex.errorCode)
            assertFailsWith<AppException> {
                provider.confirmUser("invalid-token", "invalid-token-id")
            }
        }
    }

    @Test
    fun confirmUser_invalidArgumentsThrows() {
        val provider = app.emailPasswordAuth
        runBlocking {
            assertFailsWith<IllegalArgumentException> { provider.confirmUser("", "token-id") }
            assertFailsWith<IllegalArgumentException> { provider.confirmUser("token", "") }
        }
    }

    @Test
    fun resendConfirmationEmail_userAlreadyConfirmedThrows() = runBlocking {
        val email = TestHelper.randomEmail()
        val provider = app.emailPasswordAuth
        provider.registerUser(email, "123456")
        assertFailsWith<UserAlreadyConfirmedException> { provider.resendConfirmationEmail(email) }
        Unit
    }

    @Test
    fun resendConfirmationEmail_invalidArgumentsThrows() = runBlocking {
        val provider: EmailPasswordAuth = app.emailPasswordAuth
        assertFailsWith<IllegalArgumentException> { provider.resendConfirmationEmail("") }
        Unit
    }

    @Test
    fun sendResetPasswordEmail() = runBlocking {
        val provider = app.emailPasswordAuth
        val email = TestHelper.randomEmail()
        provider.registerUser(email, "123456")
        provider.sendResetPasswordEmail(email)
    }

    @Test
    fun sendResetPasswordEmail_noUserThrows() = runBlocking {
        val provider = app.emailPasswordAuth
        val error = assertFailsWith<UserNotFoundException> { provider.sendResetPasswordEmail("unknown@10gen.com") }
        assertTrue(error.message!!.contains("user not found"), error.message)
    }

    @Test
    fun sendResetPasswordEmail_invalidArgumentsThrows() = runBlocking {
        val provider = app.emailPasswordAuth
        assertFailsWith<IllegalArgumentException> { provider.sendResetPasswordEmail("") }
        Unit
    }

    @Test
    fun callResetPasswordFunction() {
        val provider = app.emailPasswordAuth
        val adminApi = app.asTestApp
        runBlocking {
            adminApi.setResetFunction(enabled = true)
            val email = TestHelper.randomEmail()
            provider.registerUser(email, "123456")
            try {
                provider.callResetPasswordFunction(email, "new-password", "say-the-magic-word", 42)
                val user = app.login(Credentials.emailPassword(email, "new-password"))
                user.logOut()
            } finally {
                adminApi.setResetFunction(enabled = false)
            }
        }
    }

    @Test
    fun callResetPasswordFunction_invalidServerArgsThrows() {
        val provider = app.emailPasswordAuth
        val adminApi = app.asTestApp
        runBlocking {
            adminApi.setResetFunction(enabled = true)
            val email = TestHelper.randomEmail()
            provider.registerUser(email, "123456")
            try {
                provider.callResetPasswordFunction(email, "new-password", "wrong-magic-word")
            } catch (error: ServiceException) {
                assertTrue(error.message!!.contains("failed to reset password for user \"$email\""), error.message)
            } finally {
                adminApi.setResetFunction(enabled = false)
            }
        }
    }

    @Test
    fun callResetPasswordFunction_invalidArgumentsThrows() {
        val provider = app.emailPasswordAuth
        val adminApi = app.asTestApp
        runBlocking {
            adminApi.setResetFunction(enabled = true)
            val email = TestHelper.randomEmail()
            assertFailsWith<IllegalArgumentException> { provider.callResetPasswordFunction("", "password") }
            assertFailsWith<IllegalArgumentException> { provider.callResetPasswordFunction(email, "") }
            assertFailsWith<IllegalArgumentException> { provider.callResetPasswordFunction(email, "new-password2", object {}) }
        }
    }

    @Ignore
    @Test
    fun resetPassword() {
        TODO("Find a way to test this.")
    }

    @Test
    fun resetPassword_wrongArgumentTypesThrows() = runBlocking {
        val provider = app.emailPasswordAuth
        try {
            provider.resetPassword("invalid-token", "invalid-token-id", "new-password")
        } catch (error: BadRequestException) {
            assertTrue(error.message!!.contains("invalid token data"), error.message)
        }
    }

    @Ignore
    @Test
    fun resetPassword_noUserFoundThrows() {
        // If the token data is valid but the user no longer exists, a different
        // error is thrown: https://github.com/10gen/baas/blob/master/authprovider/providers/local/password_store_test.go
        // Find a way to test this.
    }

    @Test
    fun resetPassword_invalidArgumentsThrows() = runBlocking {
        val provider = app.emailPasswordAuth
        assertFailsWith<IllegalArgumentException> { provider.resetPassword("", "token-id", "password") }
        assertFailsWith<IllegalArgumentException> { provider.resetPassword("token", "", "password") }
        assertFailsWith<IllegalArgumentException> { provider.resetPassword("token", "token-id", "") }
        Unit
    }
}

class EmailPasswordAuthWithEmailConfirmTests {
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(
            this::class.simpleName,
            object : BaseAppInitializer(
                syncServerAppName("em-cnfrm"),
                { app: BaasApp -> addEmailProvider(app, autoConfirm = false) }
            ) {}
        )
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun resendConfirmationEmail() = runBlocking {
        // We only test that the server successfully accepts the request. We have no way of knowing
        // if the Email was actually sent.
        // TODO Figure out a way to check if this actually happened. Perhaps a custom SMTP server?
        val email = TestHelper.randomEmail()
        val provider = app.emailPasswordAuth
        provider.registerUser(email, "123456")
        provider.resendConfirmationEmail(email)
    }

    @Test
    fun resendConfirmationEmail_noUserThrows() = runBlocking {
        val email = TestHelper.randomEmail()
        val provider = app.emailPasswordAuth
        provider.registerUser(email, "123456")
        val error = assertFailsWith<UserNotFoundException> { provider.resendConfirmationEmail("foo") }
        assertTrue(error.message!!.contains("user not found"), error.message)
    }
}

class EmailPasswordAuthWithCustomFunctionTests {
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(
            this::class.simpleName,
            object : BaseAppInitializer(
                syncServerAppName("em-cstm"),
                { app ->
                    addEmailProvider(app, autoConfirm = false, runConfirmationFunction = true)
                }
            ) {}
        )
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun retryCustomConfirmation() = runBlocking {
        val (email, password) = "realm_pending_${TestHelper.randomEmail()}" to "123456"
        val provider = app.emailPasswordAuth
        provider.registerUser(email, password) // Will move to "pending"
        assertFailsWith<AuthException> {
            app.login(Credentials.emailPassword(email, password))
        }
        provider.retryCustomConfirmation(email) // Will properly "confirm"
        app.login(Credentials.emailPassword(email, password))
        Unit
    }

    @Test
    fun retryCustomConfirmation_failConfirmation() = runBlocking {
        // Only emails containing realm_tests_do_autoverify will be confirmed
        val email = "do_not_confirm_${TestHelper.randomEmail()}"
        val provider = app.emailPasswordAuth
        val exception = assertFailsWith<UserNotFoundException> {
            provider.retryCustomConfirmation(email)
        }
        assertTrue(exception.message!!.contains("user not found"), exception.message)
    }

    @Test
    fun retryCustomConfirmation_noUserThrows() = runBlocking {
        val email = "realm_pending_${TestHelper.randomEmail()}"
        val provider = app.emailPasswordAuth
        provider.registerUser(email, "123456")
        try {
            provider.retryCustomConfirmation("foo@gen.com")
            fail()
        } catch (error: UserNotFoundException) {
            assertTrue(error.message!!.contains("user not found"), error.message)
        }
    }

    @Test
    fun retryCustomConfirmation_alreadyConfirmedThrows() = runBlocking {
        val email = "realm_verify_${TestHelper.randomEmail()}"
        val provider = app.emailPasswordAuth
        provider.registerUser(email, "123456")
        assertFailsWith<UserAlreadyConfirmedException> {
            provider.retryCustomConfirmation(email)
        }
        Unit
    }

    @Test
    fun retryCustomConfirmation_invalidArgumentsThrows() = runBlocking {
        val provider: EmailPasswordAuth = app.emailPasswordAuth
        assertFailsWith<IllegalArgumentException> { provider.retryCustomConfirmation("") }
        Unit
    }
}
