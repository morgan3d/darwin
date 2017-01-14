/** Dangerous creature rooted in place. Continuously spins to the left
 * and blindly attacks. */
public class Flytrap extends Creature {
    public void run() {
        while (true) {
            attack();
            turnLeft();
        }
    }

    public String getAuthorName() {
        return "Darwin SDK";
    }

    public String getDescription() {
        return "Dangerous creature rooted in place. Continuously " +
            "spins to the left and blindly attacks.";
    }
}
