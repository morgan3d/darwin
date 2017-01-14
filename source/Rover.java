/** Minimal creature that blindly moves and attacks.*/
public class Rover extends Creature {
    public void run() {
        while (true) {
            if (! moveForward()) {
                attack();
                turnLeft();
            }
        }
    }

    public String getAuthorName() {
        return "Darwin SDK";
    }

    public String getDescription() {
        return "Minimal creature that blindly moves and attacks.";
    }
}
