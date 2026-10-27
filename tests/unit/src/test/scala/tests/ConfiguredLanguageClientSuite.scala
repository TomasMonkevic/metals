package tests

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService

import scala.meta.internal.metals.ClientConfiguration
import scala.meta.internal.metals.MetalsServerConfig
import scala.meta.internal.metals.clients.language.ConfiguredLanguageClient
import scala.meta.internal.metals.clients.language.DelegatingLanguageClient
import scala.meta.internal.metals.clients.language.NoopLanguageClient

import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.SemanticTokensWorkspaceCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities

/**
 * Regression tests for https://github.com/scalameta/metals/issues/8961.
 *
 * lsp4j declares the refresh endpoints as `default` methods that throw
 * `UnsupportedOperationException` synchronously, so a wrapper that forgets to
 * forward one breaks every operation that triggers a refresh.
 */
class ConfiguredLanguageClientSuite extends BaseSuite {

  implicit val ex: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  /** A client that advertises `workspace.semanticTokens.refreshSupport`. */
  private def semanticTokensRefreshConfig: ClientConfiguration = {
    val workspace = new WorkspaceClientCapabilities()
    workspace.setSemanticTokens(new SemanticTokensWorkspaceCapabilities(true))
    val capabilities = new ClientCapabilities()
    capabilities.setWorkspace(workspace)
    val params = new InitializeParams()
    params.setCapabilities(capabilities)
    ClientConfiguration(MetalsServerConfig(), params)
  }

  test("refresh-semantic-tokens-survives-sync-throw") {
    val throwing = new NoopLanguageClient {
      override def refreshSemanticTokens(): CompletableFuture[Void] =
        throw new UnsupportedOperationException()
    }
    val client =
      new ConfiguredLanguageClient(throwing, semanticTokensRefreshConfig, None)

    val result = client.refreshSemanticTokens()

    assert(result.isDone(), "refresh should complete instead of throwing")
    assert(
      !result.isCompletedExceptionally(),
      "a failed refresh must not fail the operation that triggered it",
    )
  }

  test("delegating-client-forwards-refresh-semantic-tokens") {
    val refreshed = new AtomicBoolean(false)
    val recording = new NoopLanguageClient {
      override def refreshSemanticTokens(): CompletableFuture[Void] = {
        refreshed.set(true)
        CompletableFuture.completedFuture(null)
      }
    }

    new DelegatingLanguageClient(recording).refreshSemanticTokens()

    assert(refreshed.get(), "refreshSemanticTokens was not forwarded")
  }

  test("delegating-client-forwards-refresh-code-lenses") {
    val refreshed = new AtomicBoolean(false)
    val recording = new NoopLanguageClient {
      override def refreshCodeLenses(): CompletableFuture[Void] = {
        refreshed.set(true)
        CompletableFuture.completedFuture(null)
      }
    }

    new DelegatingLanguageClient(recording).refreshCodeLenses()

    assert(refreshed.get(), "refreshCodeLenses was not forwarded")
  }
}
