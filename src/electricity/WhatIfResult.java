package electricity;

public class WhatIfResult{
    private final double oldUnits;
    private final double oldBill;
    private final double newUnits;
    private final double newBill;

    public WhatIfResult(double oldUnits, double oldBill, double newUnits, double newBill) {
        this.oldUnits = oldUnits;
        this.oldBill = oldBill;
        this.newUnits = newUnits;
        this.newBill = newBill;
    }

    public double getOldUnits() { return oldUnits; }
    public double getOldBill() { return oldBill; }
    public double getNewUnits() { return newUnits; }
    public double getNewBill() { return newBill; }

    // Positive = money saved, negative = the bill went up
    public double savingPerMonth() { return oldBill - newBill; }
    public double savingPerYear() { return savingPerMonth() * 12; }
}
