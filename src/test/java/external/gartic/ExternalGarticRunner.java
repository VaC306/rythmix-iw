package external.gartic;

import com.intuit.karate.junit5.Karate;

public class ExternalGarticRunner {

    @Karate.Test
    public Karate garticGame() {
        return Karate.run("gartic").relativeTo(getClass());
    }
}