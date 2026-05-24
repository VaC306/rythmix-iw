package external.adminTest;

import com.intuit.karate.junit5.Karate;

public class ExternalAdminTestRunner {

    @Karate.Test
    public Karate garticGame() {
        return Karate.run("adminTest").relativeTo(getClass());
    }

}
