package electricity;

public class Tariff {
    private double[] slabLimits;   // upper limit of each slab, in units
    private double[] rates;        // rupees per unit for each slab
    private double fixedCharge;    // flat monthly charge

    // Sample slabs for the demo. Replace with your state's real tariff later.
    public Tariff() {
        this(new double[] {100, 200, 300, Double.MAX_VALUE},
             new double[] {3.0, 5.0, 7.0, 9.0},
             0.0);
    }

    public Tariff(double[] slabLimits, double[] rates, double fixedCharge) {
        if (slabLimits.length != rates.length || slabLimits.length == 0)
            throw new IllegalArgumentException("Each slab needs a limit and a rate");
        if (fixedCharge < 0)
            throw new IllegalArgumentException("Fixed charge cannot be negative");

        double previous = 0;
        for (int i = 0; i < slabLimits.length; i++) {
            if (slabLimits[i] <= previous)
                throw new IllegalArgumentException("Slab limits must keep increasing");
            if (rates[i] < 0)
                throw new IllegalArgumentException("Rates cannot be negative");
            previous = slabLimits[i];
        }
        if (slabLimits[slabLimits.length - 1] != Double.MAX_VALUE)
            throw new IllegalArgumentException("The last slab must have no upper limit");

        this.slabLimits = slabLimits.clone();
        this.rates = rates.clone();
        this.fixedCharge = fixedCharge;
    }

    public double calculateBill(double units) {
        if (units < 0)
            throw new IllegalArgumentException("Units cannot be negative");

        double remaining = units;
        double previousLimit = 0;
        double bill = 0;

        for (int i = 0; i < slabLimits.length; i++) {
            double slabSize = slabLimits[i] - previousLimit;
            double unitsInSlab = Math.min(remaining, slabSize);

            bill += unitsInSlab * rates[i];
            remaining -= unitsInSlab;
            previousLimit = slabLimits[i];

            if (remaining <= 0) break;
        }
        return bill + fixedCharge;
    }

    // How many units 'units' is above the start of its slab; -1 if it is in the first slab
    public double unitsAboveSlabStart(double units) {
        double previousLimit = 0;
        for (int i = 0; i < slabLimits.length; i++) {
            if (units <= slabLimits[i]) {
                return i == 0 ? -1 : units - previousLimit;
            }
            previousLimit = slabLimits[i];
        }
        return -1;
    }

    // Readable list of the slabs, used in the report
    public String describe() {
        StringBuilder sb = new StringBuilder();
        double previous = 0;
        for (int i = 0; i < slabLimits.length; i++) {
            if (slabLimits[i] == Double.MAX_VALUE) {
                sb.append(String.format("  Above %.0f units : Rs %.2f per unit%n", previous, rates[i]));
            } else {
                sb.append(String.format("  %.0f - %.0f units : Rs %.2f per unit%n", previous, slabLimits[i], rates[i]));
            }
            previous = slabLimits[i];
        }
        if (fixedCharge > 0) {
            sb.append(String.format("  Fixed charge : Rs %.2f%n", fixedCharge));
        }
        return sb.toString();
    }
    // Copies, so outside code cannot change the slabs
    public double[] getSlabLimits() { return slabLimits.clone(); }
    public double[] getRates() { return rates.clone(); }

    public double getFixedCharge() { return fixedCharge; }
}