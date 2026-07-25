package com.studio.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Manages shared Playwright BrowserContext for multi-case runs in-process.
 */
public class SharedBrowserSession implements AutoCloseable {

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page sharedPage;

    /**
     * Start a shared session. browserId = "CHROMIUM", "FIREFOX", "WEBKIT", "CHROME", "EDGE".
     */
    public void start(boolean headless, String browserId) {
        playwright = Playwright.create();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(headless);
        String id = browserId != null ? browserId.toUpperCase() : "CHROMIUM";

        switch (id) {
            case "FIREFOX":
                browser = playwright.firefox().launch(opts);
                break;
            case "WEBKIT":
                browser = playwright.webkit().launch(opts);
                break;
            case "CHROME":
                browser = playwright.chromium().launch(opts.setChannel("chrome"));
                break;
            case "EDGE":
                browser = playwright.chromium().launch(opts.setChannel("msedge"));
                break;
            default:
                browser = playwright.chromium().launch(opts);
                break;
        }

        context = browser.newContext();
        sharedPage = context.newPage();
    }

    public Page page() { return sharedPage; }
    public BrowserContext context() { return context; }

    @Override
    public void close() {
        Page page = sharedPage;
        BrowserContext ctx = context;
        Browser br = browser;
        Playwright pw = playwright;
        sharedPage = null;
        context = null;
        browser = null;
        playwright = null;
        if (page != null) {
            try { page.close(); } catch (Exception e) {
                System.err.println("[SharedBrowserSession] Error closing page: " + e.getMessage());
            }
        }
        if (ctx != null) {
            try { ctx.close(); } catch (Exception e) {
                System.err.println("[SharedBrowserSession] Error closing context: " + e.getMessage());
            }
        }
        if (br != null) {
            try { br.close(); } catch (Exception e) {
                System.err.println("[SharedBrowserSession] Error closing browser: " + e.getMessage());
            }
        }
        if (pw != null) {
            try { pw.close(); } catch (Exception e) {
                System.err.println("[SharedBrowserSession] Error closing playwright: " + e.getMessage());
            }
        }
    }
}
