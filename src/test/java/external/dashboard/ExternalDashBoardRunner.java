package external.dashboard;

import com.intuit.karate.junit5.Karate;

public class ExternalDashBoardRunner {

    @Karate.Test
    public Karate garticGame() {
        return Karate.run("dashboard-admin").relativeTo(getClass());
    }

}
