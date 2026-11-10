package electricity;

public class Appliance {
    private String name;
    private double watts;
    private double hoursPerDay;
    private int quantity;

    public Appliance(String name, double watts, double hoursPerDay, int quantity) {
        setName(name);
        setWatts(watts);
        setHoursPerDay(hoursPerDay);
        setQuantity(quantity);
    }

    // Units (kWh) used in a month
    public double monthlyKwh() {
        return (watts * hoursPerDay * quantity * Constants.DAYS_PER_MONTH) / 1000.0;
    }

    public String getName() { return name; }
    public double getWatts() { return watts; }
    public double getHoursPerDay() { return hoursPerDay; }
    public int getQuantity() { return quantity; }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Name cannot be empty");
        this.name = name.trim();
    }

    public void setWatts(double watts) {
        if (watts <= 0)
            throw new IllegalArgumentException("Watts must be greater than 0");
        this.watts = watts;
    }

    public void setHoursPerDay(double hoursPerDay) {
        if (hoursPerDay <= 0 || hoursPerDay > 24)
            throw new IllegalArgumentException("Hours per day must be between 0 and 24");
        this.hoursPerDay = hoursPerDay;
    }

    public void setQuantity(int quantity) {
        if (quantity < 1)
            throw new IllegalArgumentException("Quantity must be at least 1");
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return String.format("%s (%.0f W, %.1f h/day, x%d)", name, watts, hoursPerDay, quantity);
    }
}