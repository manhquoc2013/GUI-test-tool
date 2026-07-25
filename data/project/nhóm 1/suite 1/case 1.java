import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import java.util.*;

public class Example {
  public static void main(String[] args) {
    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
        .setHeadless(false));
      BrowserContext context = browser.newContext();
      Page page = context.newPage();
      page.navigate("https://github.com/manhquoc2013/fe-simulator");
      page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email address")).click();
      page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email address")).fill("manhquoc11@gmail.com");
      page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username or email address")).press("Tab");
      page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password")).fill("manhquoc11@9");
      page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
      page.getByText("We sent you a sign-in request on your GitHub Mobile app. Enter the digits shown").click();
      page.close();
    }
  }
}