package iso.shellbridge;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CommandTest {

    @Test
    void runCapturesOutputWithoutHanging() {
        Command command = new Command("printf 'hello\\nworld\\n'");
        assertTimeoutPreemptively(Duration.ofSeconds(2), command::run);
        assertEquals("hello\nworld\n", command.getOutput());
    }

    @Test
    void getOutputArrayReturnsEmptyArrayWhenOutputIsEmpty() throws Exception {
        Command command = new Command("true");
        command.exec();
        assertArrayEquals(new String[0], command.getOutputArray());
    }

    @Test
    void execResetsPreviousOutput() throws Exception {
        Command command = new Command("printf 'one\\n'");
        command.exec();
        command.setCommand("printf 'two\\n'");
        command.exec();
        assertEquals("two\n", command.getOutput());
    }
}
