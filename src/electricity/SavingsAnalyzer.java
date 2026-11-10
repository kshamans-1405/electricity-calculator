package electricity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SavingsAnalyzer {
    // Editable values: change these to tune the suggestions
    private static final double HIGH_SHARE_PERCENT = 30.0;   // flag an appliance above this share
    private static final double LED_TUBE_WATTS = 18;
    private static final double LED_BULB_WATTS = 9;
    private static final double BLDC_FAN_WATTS = 35;
    private static final double NEAR_SLAB_FRACTION = 0.20;   // "close to the slab below"

    private final BillCalculator calc;

    public SavingsAnalyzer(BillCalculator calc) {
        this.calc = calc;
    }

    // Appliances sorted from highest to lowest monthly units
    public List<Appliance> rankByUsage() {
        List<Appliance> ranked = new ArrayList<>(calc.getAppliances());
        ranked.sort(Comparator.comparingDouble(Appliance::monthlyKwh).reversed());
        return ranked;
    }

    public double shareOf(Appliance appliance) {
        double total = calc.totalUnits();
        if (total == 0) return 0;
        return appliance.monthlyKwh() / total * 100;
    }

    public List<Suggestion> suggestions() {
        List<Suggestion> list = new ArrayList<>();

        for (Appliance a : calc.getAppliances()) {
            String n = a.getName().toLowerCase();
            double perHourPerMonth = a.getQuantity() * Constants.DAYS_PER_MONTH / 1000.0;

            // Rule 1: one appliance uses a big share of the units
            if (shareOf(a) > HIGH_SHARE_PERCENT && a.getHoursPerDay() > 1) {
                double units = a.getWatts() * perHourPerMonth;   // 1 hour less per day
                list.add(make(String.format("%s uses %.1f%% of your units. Using it 1 hour less per day",
                        a.getName(), shareOf(a)), units));
            }

            // Rule 2: old lighting -> LED
            boolean isTube = n.contains("tube") && !n.contains("led");
            boolean isOldBulb = (n.contains("bulb") || n.contains("cfl") || n.contains("incandescent"))
                    && !n.contains("led");
            if (isTube || isOldBulb) {
                double ledWatts = isTube ? LED_TUBE_WATTS : LED_BULB_WATTS;
                if (a.getWatts() > ledWatts) {
                    double units = (a.getWatts() - ledWatts) * a.getHoursPerDay() * perHourPerMonth;
                    list.add(make(String.format("Replace %d x %s (%.0f W) with LED (%.0f W)",
                            a.getQuantity(), a.getName(), a.getWatts(), ledWatts), units));
                }
            }

            // Rule 3: fans running many hours -> BLDC fan
            if (n.contains("fan") && a.getHoursPerDay() > 8 && a.getWatts() > BLDC_FAN_WATTS) {
                double units = (a.getWatts() - BLDC_FAN_WATTS) * a.getHoursPerDay() * perHourPerMonth;
                list.add(make(String.format("Switch %d x %s (%.0f W) to a BLDC fan (%.0f W)",
                        a.getQuantity(), a.getName(), a.getWatts(), BLDC_FAN_WATTS), units));
            }
        }

        // Rule 4: only a few units above the start of the current slab
        double total = calc.totalUnits();
        double above = calc.getTariff().unitsAboveSlabStart(total);
        if (above > 0 && above <= NEAR_SLAB_FRACTION * total) {
            list.add(make(String.format("You are only %.1f units into your current slab. "
                    + "Cutting just that much moves you down a slab", above), above));
        }

        list.sort(Comparator.comparingDouble(Suggestion::getRupeesSaved).reversed());
        return list;
    }

    // What would the bill be if one appliance's hours and quantity changed?
    // Works on a copy, so the real appliance list is never changed.
    public WhatIfResult whatIf(int index, double newHours, int newQuantity) {
        List<Appliance> original = calc.getAppliances();
        if (index < 0 || index >= original.size())
            throw new IllegalArgumentException("No appliance at position " + (index + 1));

        BillCalculator copy = new BillCalculator(calc.getTariff());
        for (int i = 0; i < original.size(); i++) {
            Appliance a = original.get(i);
            if (i == index) {
                copy.addAppliance(new Appliance(a.getName(), a.getWatts(), newHours, newQuantity));
            } else {
                copy.addAppliance(new Appliance(a.getName(), a.getWatts(), a.getHoursPerDay(), a.getQuantity()));
            }
        }
        return new WhatIfResult(calc.totalUnits(), calc.totalBill(), copy.totalUnits(), copy.totalBill());
    }
    private Suggestion make(String text, double unitsSaved) {
        double now = calc.totalUnits();
        double after = Math.max(0, now - unitsSaved);
        Tariff tariff = calc.getTariff();
        double rupees = tariff.calculateBill(now) - tariff.calculateBill(after);
        return new Suggestion(text, unitsSaved, rupees);
    }
}