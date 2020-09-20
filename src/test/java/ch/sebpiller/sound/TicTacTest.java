package ch.sebpiller.sound;

import ch.sebpiller.iot.bluetooth.luke.roberts.lamp.f.LukeRobertsLampF;
import ch.sebpiller.iot.lamp.SmartLampFacade;
import org.junit.Test;

public class TicTacTest {
    @Test
    public void tictacTest() {
        SmartLampFacade lampF = new LukeRobertsLampF();

        TicTac.BeatListener beatListener = ticOrTac -> {
            if (ticOrTac) {
                lampF.fadeBrightnessFromTo((byte) 100, (byte) 0, SmartLampFacade.FadeStyle.FAST);
            } else {
                lampF.power(false).power(true);
            }

            return false;
        };
        TicTac ticTac = new TicTabBuilder()

               // .withListener(beatListener)
                .build()
                ;

        // wait for data processing...
        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
        }
    }
}