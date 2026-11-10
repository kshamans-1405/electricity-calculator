package electricity;

public class Suggestion {
    private final String text;
    private final double unitsSaved;
    private final double rupeesSaved;

    public Suggestion(String text, double unitsSaved, double rupeesSaved) {
        this.text = text;
        this.unitsSaved = unitsSaved;
        this.rupeesSaved = rupeesSaved;
    }

    public String getText() { return text; }
    public double getUnitsSaved() { return unitsSaved; }
    public double getRupeesSaved() { return rupeesSaved; }
    public double getRupeesSavedPerYear() { return rupeesSaved * 12; }
}
