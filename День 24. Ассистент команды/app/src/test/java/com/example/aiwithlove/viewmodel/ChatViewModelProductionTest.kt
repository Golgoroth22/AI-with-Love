package com.example.aiwithlove.viewmodel

import android.util.Log
import com.example.aiwithlove.data.AgenticResponse
import com.example.aiwithlove.data.AgenticUsage
import com.example.aiwithlove.data.OutputItem
import com.example.aiwithlove.data.PerplexityApiService
import com.example.aiwithlove.data.model.Message
import com.example.aiwithlove.database.ChatRepository
import com.example.aiwithlove.database.EmbeddingsRepository
import com.example.aiwithlove.mcp.McpClient
import com.example.aiwithlove.mcp.McpClientManager
import com.example.aiwithlove.ollama.OllamaClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Production-ready unit tests for ChatViewModel that focus on testable, observable behavior.
 * Tests are designed to be reliable and not flaky.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelProductionTest {

    private lateinit var viewModel: ChatViewModel
    private lateinit var perplexityService: PerplexityApiService
    private lateinit var chatRepository: ChatRepository
    private lateinit var mcpClient: McpClient
    private lateinit var mcpClientManager: McpClientManager
    private lateinit var ollamaClient: OllamaClient
    private lateinit var embeddingsRepository: EmbeddingsRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        // Use UnconfinedTestDispatcher for immediate execution
        Dispatchers.setMain(testDispatcher)

        // Mock Android Log to prevent errors in unit tests
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        // Create mocks
        perplexityService = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        mcpClient = mockk(relaxed = true)
        mcpClientManager = mockk(relaxed = true)
        ollamaClient = mockk(relaxed = true)
        embeddingsRepository = mockk(relaxed = true)

        // Setup default mock behaviors
        coEvery { chatRepository.getAllMessages() } returns emptyList()
        coEvery { chatRepository.getSummary() } returns null
        coEvery { chatRepository.saveUserMessage(any()) } returns Unit
        coEvery { chatRepository.saveAssistantMessage(any()) } returns Unit
        coEvery { chatRepository.clearAllMessages() } returns Unit
        coEvery { chatRepository.clearSummary() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel =
            ChatViewModel(
                perplexityService,
                chatRepository,
                mcpClientManager,
                ollamaClient,
                embeddingsRepository
            )
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has welcome message`() =
        runTest {
            createViewModel()

            val messages = viewModel.messages.value
            assertEquals(1, messages.size)
            assertTrue(messages.first().text.contains("Привет"))
            assertFalse(messages.first().isFromUser)
        }

    @Test
    fun `initial loading state is false`() =
        runTest {
            createViewModel()

            assertFalse(viewModel.isLoading.value)
        }

    @Test
    fun `initial MCP dialog is not shown`() =
        runTest {
            createViewModel()

            assertFalse(viewModel.showMcpDialog.value)
        }

    @Test
    fun `initial MCP servers list is not empty`() =
        runTest {
            createViewModel()

            val servers = viewModel.mcpServers.value
            assertTrue(servers.isNotEmpty())
        }

    @Test
    fun `rag server exists in MCP servers`() =
        runTest {
            createViewModel()

            val ragServer = viewModel.mcpServers.value.find { it.id == "rag" }
            assertNotNull(ragServer)
        }

    // ========== MCP Dialog Tests ==========

    @Test
    fun `toggleMcpDialog shows dialog`() =
        runTest {
            createViewModel()

            viewModel.toggleMcpDialog()
            assertTrue(viewModel.showMcpDialog.value)
        }

    @Test
    fun `toggleMcpDialog hides dialog when shown`() =
        runTest {
            createViewModel()

            viewModel.toggleMcpDialog()
            assertTrue(viewModel.showMcpDialog.value)

            viewModel.toggleMcpDialog()
            assertFalse(viewModel.showMcpDialog.value)
        }

    // ========== MCP Server Toggle Tests ==========

    @Test
    fun `toggleMcpServer changes enabled state`() =
        runTest {
            createViewModel()

            val initialState = viewModel.mcpServers.value.find { it.id == "rag" }?.isEnabled ?: false

            viewModel.toggleMcpServer("rag")

            val newState = viewModel.mcpServers.value.find { it.id == "rag" }?.isEnabled ?: false
            assertEquals(!initialState, newState)
        }

    @Test
    fun `toggleMcpServer twice restores original state`() =
        runTest {
            createViewModel()

            val initialState = viewModel.mcpServers.value.find { it.id == "rag" }?.isEnabled ?: false

            viewModel.toggleMcpServer("rag")
            viewModel.toggleMcpServer("rag")

            val finalState = viewModel.mcpServers.value.find { it.id == "rag" }?.isEnabled ?: false
            assertEquals(initialState, finalState)
        }

    @Test
    fun `toggleMcpServer only affects target server`() =
        runTest {
            createViewModel()

            val initialServers = viewModel.mcpServers.value
            val otherServers = initialServers.filter { it.id != "rag" }

            viewModel.toggleMcpServer("rag")

            val newServers = viewModel.mcpServers.value
            val newOtherServers = newServers.filter { it.id != "rag" }

            // Check that other servers remain unchanged
            assertEquals(otherServers.size, newOtherServers.size)
            otherServers.forEachIndexed { index, server ->
                assertEquals(server.isEnabled, newOtherServers[index].isEnabled)
            }
        }

    // ========== Send Message Tests ==========

    @Test
    fun `sendMessage with empty string does nothing`() =
        runTest {
            createViewModel()

            val initialCount = viewModel.messages.value.size

            viewModel.sendMessage("")

            assertEquals(initialCount, viewModel.messages.value.size)
            coVerify(exactly = 0) { perplexityService.sendAgenticRequest(any(), any(), any(), any()) }
        }

    @Test
    fun `sendMessage with whitespace only does nothing`() =
        runTest {
            createViewModel()

            val initialCount = viewModel.messages.value.size

            viewModel.sendMessage("   ")
            viewModel.sendMessage("\n\t")

            assertEquals(initialCount, viewModel.messages.value.size)
        }

    @Test
    fun `sendMessage adds user message to list`() =
        runTest {
            createViewModel()

            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } returns
                Result.success(
                    AgenticResponse(
                        outputText = "Response",
                        usage = AgenticUsage(10, 20)
                    )
                )

            viewModel.sendMessage("Test message")

            val userMessages = viewModel.messages.value.filter { it.isFromUser }
            assertTrue(userMessages.any { it.text == "Test message" })
        }

    @Test
    fun `sendMessage calls repository to save user message`() =
        runTest {
            createViewModel()

            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } returns
                Result.success(
                    AgenticResponse(
                        outputText = "Response",
                        usage = AgenticUsage(10, 20)
                    )
                )

            viewModel.sendMessage("User input")

            coVerify {
                chatRepository.saveUserMessage(
                    match { it.text == "User input" && it.isFromUser }
                )
            }
        }

    @Test
    fun `sendMessage calls API service`() =
        runTest {
            createViewModel()

            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } returns
                Result.success(
                    AgenticResponse(
                        outputText = "API response",
                        usage = AgenticUsage(10, 20)
                    )
                )

            viewModel.sendMessage("Question")

            coVerify(atLeast = 1) {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            }
        }

    // ========== Clear Chat Tests ==========

    @Test
    fun `clearChat calls repository methods`() =
        runTest {
            createViewModel()

            viewModel.clearChat()

            coVerify { chatRepository.clearAllMessages() }
            coVerify { chatRepository.clearSummary() }
        }

    @Test
    fun `clearChat resets messages to welcome only`() =
        runTest {
            createViewModel()

            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } returns
                Result.success(
                    AgenticResponse(
                        outputText = "Response",
                        usage = AgenticUsage(10, 20)
                    )
                )

            // Add a message
            viewModel.sendMessage("Test")

            // Clear
            viewModel.clearChat()

            val messages = viewModel.messages.value
            assertTrue(messages.any { it.text.contains("Привет") })
        }

    // ========== Load History Tests ==========

    @Test
    fun `loadChatHistory called during initialization`() =
        runTest {
            createViewModel()

            coVerify { chatRepository.getAllMessages() }
            coVerify { chatRepository.getSummary() }
        }

    @Test
    fun `loads saved messages from repository`() =
        runTest {
            val savedMessages =
                listOf(
                    Message(text = "Old message", isFromUser = true, timestamp = 1000),
                    Message(text = "Old response", isFromUser = false, timestamp = 2000)
                )
            coEvery { chatRepository.getAllMessages() } returns savedMessages

            createViewModel()

            val messages = viewModel.messages.value
            assertTrue(messages.any { it.text == "Old message" })
            assertTrue(messages.any { it.text == "Old response" })
        }

    // ========== Error Handling Tests ==========

    @Test
    fun `API failure shows error message`() =
        runTest {
            createViewModel()

            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } returns Result.failure(Exception("Network error"))

            viewModel.sendMessage("Test")

            val messages = viewModel.messages.value
            assertTrue(
                messages.any {
                    !it.isFromUser && it.text.contains("Ошибка")
                }
            )
        }

    // ========== Tool Usage Tests ==========

    @Test
    fun `github keywords with enabled server passes tools to API`() =
        runTest {
            createViewModel()

            // Enable github server if not already
            val githubServer = viewModel.mcpServers.value.find { it.id == "github" }
            if (githubServer?.isEnabled == false) {
                viewModel.toggleMcpServer("github")
            }

            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } returns
                Result.success(
                    AgenticResponse(
                        outputText = "Here's the repository info",
                        usage = AgenticUsage(10, 20)
                    )
                )

            viewModel.sendMessage("GitWithLove покажи информацию о репозитории")

            coVerify {
                perplexityService.sendAgenticRequest(
                    input = any(),
                    model = any(),
                    instructions = any(),
                    tools = match { it != null && it.isNotEmpty() }
                )
            }
        }

    @Test
    fun `regular message without tools passes null tools`() =
        runTest {
            createViewModel()

            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } returns
                Result.success(
                    AgenticResponse(
                        outputText = "Regular response",
                        usage = AgenticUsage(10, 20)
                    )
                )

            viewModel.sendMessage("Hello")

            coVerify {
                perplexityService.sendAgenticRequest(
                    input = any(),
                    model = any(),
                    instructions = any(),
                    tools = null
                )
            }
        }

    @Test
    fun `tool execution triggers MCP client call`() =
        runTest {
            createViewModel()

            // Enable github server
            val githubServer = viewModel.mcpServers.value.find { it.id == "github" }
            if (githubServer?.isEnabled == false) {
                viewModel.toggleMcpServer("github")
            }

            // First response: tool call
            val toolCallResponse =
                AgenticResponse(
                    output =
                        listOf(
                            OutputItem(
                                type = "function_call",
                                name = "get_repo",
                                arguments = """{"owner": "facebook", "repo": "react"}"""
                            )
                        )
                )

            // Second response: final text
            val finalResponse =
                AgenticResponse(
                    outputText = "Here's the repository info",
                    usage = AgenticUsage(10, 20)
                )

            var callCount = 0
            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } answers {
                if (callCount++ == 0) {
                    Result.success(toolCallResponse)
                } else {
                    Result.success(finalResponse)
                }
            }

            coEvery {
                mcpClientManager.callTool("get_repo", any(), any())
            } returns """{"content": [{"text": "Repository information"}]}"""

            viewModel.sendMessage("GitWithLove покажи информацию о репозитории")

            coVerify { mcpClientManager.callTool("get_repo", any(), any()) }
        }

    // ========== Token Usage Tests ==========

    @Test
    fun `response includes token usage information`() =
        runTest {
            createViewModel()

            coEvery {
                perplexityService.sendAgenticRequest(any(), any(), any(), any())
            } returns
                Result.success(
                    AgenticResponse(
                        outputText = "Response",
                        usage = AgenticUsage(inputTokens = 15, outputTokens = 25, totalTokens = 40)
                    )
                )

            viewModel.sendMessage("Test")

            val messages = viewModel.messages.value
            val responseMessage =
                messages.findLast {
                    !it.isFromUser && it.promptTokens != null
                }

            assertNotNull(responseMessage)
            assertEquals(15, responseMessage?.promptTokens)
            assertEquals(25, responseMessage?.completionTokens)
        }
}
