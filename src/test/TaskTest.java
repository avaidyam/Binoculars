package test;

import org.kihara.tasks.Task;
import org.kihara.tasks.TaskScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/*
whereami       prints current node
leave-node     returns to global mode
send           sends a message to an nuclei
work-load      prints CPU load
ram-usage      prints RAM usage
statistics     prints statistics
interfaces     prints all interfaces
direct-routes  prints all connected nodes
list-actors    prints all known actors


also: http://stackoverflow.com/questions/22866901/using-java-with-nvidia-gpus-cuda
task parallel = fork/join on cpus
data parallel = gpgpu api
data-task parallel = fork/join ON gpgpu api

also: http://www.actor-framework.org/doc/examples.html
 */

public class TaskTest {

    static TaskScheduler scheduler = new TaskScheduler();

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testAdapt() throws Exception {
        scheduler.submit(Task.adapt((Integer a, Integer b) -> {
            try {
                int outcome = (a + b) / 0;
                System.out.println("Got " + outcome);
            } catch (ArithmeticException e) {
                throw e;
            } finally {
                System.err.println("The outcome was probably defined...");
            }
        }, "This should be the result of the execution."));
    }
}