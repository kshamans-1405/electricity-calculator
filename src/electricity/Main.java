package electricity;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class Main {
	private static final Scanner scanner = new Scanner(System.in);

	// Typical wattages for the demo. Check the label on your own appliance for the
	// exact value.
	private static final String[] PRESET_NAMES = { "Air conditioner", "Ceiling fan", "LED bulb", "Tube light",
			"Refrigerator", "Television", "Washing machine", "Water heater (geyser)", "Laptop" };
	private static final double[] PRESET_WATTS = { 1500, 75, 9, 40, 150, 100, 500, 2000, 60 };

	// Remember the latest what-if so it can be included in the saved report
	private static WhatIfResult lastWhatIf = null;
	private static String lastWhatIfText = "";

	public static void main(String[] args) {
		BillCalculator calc = new BillCalculator(new Tariff());
		boolean running = true;

		while (running) {
			showMenu();
			int choice = readInt("Choose: ", 1, 8);

			switch (choice) {
			case 1:
				addAppliance(calc);
				break;
			case 2:
				manageAppliances(calc);
				break;
			case 4:
				showBill(calc);
				break;
			case 5:
				showSavings(calc);
				break;
			case 6:
				showWhatIf(calc);
				break;
			case 7:
				saveReport(calc);
				break;
			case 3:
				editTariff(calc);
				break;
			case 8:
				running = false;
				System.out.println("Goodbye!");
				break;
			}
		}
	}

	private static void showMenu() {
		System.out.println();
		System.out.println("===== ELECTRICITY CALCULATOR =====");
		System.out.println("1. Add appliance");
		System.out.println("2. View / remove appliances");
		System.out.println("3. Edit tariff slabs");
		System.out.println("4. Calculate bill");
		System.out.println("5. Savings analysis");
		System.out.println("6. What-if comparison");
		System.out.println("7. Save report to file");
		System.out.println("8. Exit");
	}

	private static void addAppliance(BillCalculator calc) {
		System.out.println();
		for (int i = 0; i < PRESET_NAMES.length; i++) {
			System.out.printf("%d. %s (%.0f W)%n", i + 1, PRESET_NAMES[i], PRESET_WATTS[i]);
		}
		int customOption = PRESET_NAMES.length + 1;
		System.out.println(customOption + ". Custom appliance");

		int pick = readInt("Choose: ", 1, customOption);
		String name;
		double watts;

		if (pick == customOption) {
			name = readText("Name: ");
			watts = readDouble("Power in watts: ", 1, 10000);
		} else {
			name = PRESET_NAMES[pick - 1];
			watts = PRESET_WATTS[pick - 1];
			System.out.println("Selected: " + name);
		}

		if (name.equals("Refrigerator")) {
			System.out.println("Tip: the compressor runs only part of the day, so enter about 8 hours.");
		}

		double hours = readDouble("Hours used per day (0.1 to 24): ", 0.1, 24);
		int quantity = readInt("Quantity: ", 1, 100);

		try {
			Appliance appliance = new Appliance(name, watts, hours, quantity);
			calc.addAppliance(appliance);
			lastWhatIf = null; // the old what-if no longer matches the list
			System.out.printf("Added: %s -> %.1f units/month%n", appliance, appliance.monthlyKwh());
		} catch (IllegalArgumentException e) {
			System.out.println("Could not add appliance: " + e.getMessage());
		}
	}

	private static void manageAppliances(BillCalculator calc) {
		if (calc.isEmpty()) {
			System.out.println("No appliances added yet.");
			return;
		}
		printAppliances(calc);
		int number = readInt("Enter a number to remove it (0 to go back): ", 0, calc.size());
		if (number > 0) {
			Appliance removed = calc.removeAppliance(number - 1);
			lastWhatIf = null; // the old what-if no longer matches the list
			System.out.println("Removed: " + removed.getName());
		}
	}

	private static void printAppliances(BillCalculator calc) {
		List<Appliance> list = calc.getAppliances();
		System.out.println();
		System.out.printf("%-4s%-24s%8s%10s%5s%12s%n", "No.", "Appliance", "Watts", "Hours/day", "Qty", "Units/month");
		for (int i = 0; i < list.size(); i++) {
			Appliance a = list.get(i);
			System.out.printf("%-4d%-24s%8.0f%10.1f%5d%12.1f%n", i + 1, a.getName(), a.getWatts(), a.getHoursPerDay(),
					a.getQuantity(), a.monthlyKwh());
		}
	}

	private static void showBill(BillCalculator calc) {
		if (calc.isEmpty()) {
			System.out.println("Add at least one appliance first.");
			return;
		}
		double total = calc.totalUnits();

		System.out.println();
		System.out.printf("%-28s%12s%8s%n", "Appliance", "Units/month", "Share");
		for (Appliance a : calc.getAppliances()) {
			String label = a.getQuantity() > 1 ? a.getName() + " (x" + a.getQuantity() + ")" : a.getName();
			System.out.printf("%-28s%12.1f%7.1f%%%n", label, a.monthlyKwh(), a.monthlyKwh() / total * 100);
		}
		System.out.println("-".repeat(48));
		System.out.printf("%-28s%12.1f%n", "Total units", total);
		System.out.printf("%-28s%12.2f%n", "Estimated bill (Rs)", calc.totalBill());
	}

	private static void showSavings(BillCalculator calc) {
		if (calc.isEmpty()) {
			System.out.println("Add at least one appliance first.");
			return;
		}
		SavingsAnalyzer analyzer = new SavingsAnalyzer(calc);

		System.out.println();
		System.out.println("Biggest energy users");
		int rank = 1;
		for (Appliance a : analyzer.rankByUsage()) {
			System.out.printf("%d. %-24s%8.1f units%8.1f%%%n", rank++, a.getName(), a.monthlyKwh(),
					analyzer.shareOf(a));
		}

		List<Suggestion> tips = analyzer.suggestions();
		System.out.println();
		System.out.println("Savings suggestions");
		if (tips.isEmpty()) {
			System.out.println("No suggestions. Your usage already looks efficient.");
			return;
		}
		int number = 1;
		for (Suggestion s : tips) {
			System.out.printf("%d. %s: saves %.1f units, Rs %.2f/month (Rs %.2f/year)%n", number++, s.getText(),
					s.getUnitsSaved(), s.getRupeesSaved(), s.getRupeesSavedPerYear());
		}
		System.out.println("Note: each saving is measured alone, so do not add them up.");
	}

	private static void showWhatIf(BillCalculator calc) {
		if (calc.isEmpty()) {
			System.out.println("Add at least one appliance first.");
			return;
		}
		printAppliances(calc);
		int number = readInt("Which appliance do you want to change (0 to go back): ", 0, calc.size());
		if (number == 0)
			return;

		Appliance chosen = calc.getAppliances().get(number - 1);
		System.out.printf("Currently: %.1f hours/day, quantity %d%n", chosen.getHoursPerDay(), chosen.getQuantity());
		double hours = readDouble("New hours per day (0.1 to 24): ", 0.1, 24);
		int quantity = readInt("New quantity: ", 1, 100);

		WhatIfResult r = new SavingsAnalyzer(calc).whatIf(number - 1, hours, quantity);

		// keep it for the report
		lastWhatIf = r;
		lastWhatIfText = chosen.getName() + " used " + hours + " h/day, quantity " + quantity;

		System.out.println();
		System.out.println("What if " + chosen.getName() + " is used " + hours + " h/day, quantity " + quantity + "?");
		System.out.printf("%-26s%12s%16s%n", "", "Current", "After change");
		System.out.printf("%-26s%12.1f%16.1f%n", "Units per month", r.getOldUnits(), r.getNewUnits());
		System.out.printf("%-26s%12.2f%16.2f%n", "Bill (Rs)", r.getOldBill(), r.getNewBill());
		if (r.savingPerMonth() >= 0) {
			System.out.printf("%-26s%28.2f%n", "Saving per month (Rs)", r.savingPerMonth());
			System.out.printf("%-26s%28.2f%n", "Saving per year (Rs)", r.savingPerYear());
		} else {
			System.out.printf("%-26s%28.2f%n", "Extra cost per month (Rs)", -r.savingPerMonth());
			System.out.printf("%-26s%28.2f%n", "Extra cost per year (Rs)", -r.savingPerYear());
		}
		System.out.println("Your real appliance list was not changed.");
	}

	private static void saveReport(BillCalculator calc) {
		if (calc.isEmpty()) {
			System.out.println("Add at least one appliance first.");
			return;
		}
		ReportWriter writer = new ReportWriter(calc, new SavingsAnalyzer(calc));
		if (lastWhatIf != null) {
			writer.setWhatIf(lastWhatIfText, lastWhatIf);
		}

		String fileName = "report.txt";
		try {
			writer.saveToFile(fileName);
			System.out.println("Report saved to " + new File(fileName).getAbsolutePath());
		} catch (IOException e) {
			System.out.println("Could not save the report: " + e.getMessage());
		}
	}

	private static void editTariff(BillCalculator calc) {
		System.out.println();
		System.out.println("Current tariff:");
		System.out.print(calc.getTariff().describe());
		System.out.println();
		System.out.println("1. Enter new slabs");
		System.out.println("2. Reset to sample slabs");
		System.out.println("0. Go back");
		int choice = readInt("Choose: ", 0, 2);

		if (choice == 0)
			return;
		if (choice == 2) {
			calc.setTariff(new Tariff());
			lastWhatIf = null; // bills changed, so the old what-if is out of date
			System.out.println("Tariff reset to the sample slabs.");
			return;
		}

		int count = readInt("How many slabs (1 to 8)? ", 1, 8);
		double[] limits = new double[count];
		double[] rates = new double[count];
		double previous = 0;

		for (int i = 0; i < count; i++) {
			if (i < count - 1) {
				limits[i] = readDouble("Slab " + (i + 1) + " upper limit in units (more than " + previous + "): ",
						previous + 0.1, 100000);
				rates[i] = readDouble("Rate for slab " + (i + 1) + " (Rs per unit): ", 0, 100);
				previous = limits[i];
			} else {
				limits[i] = Double.MAX_VALUE; // the last slab has no upper limit
				String label = previous == 0 ? "Rate for all units (Rs per unit): "
						: "Rate for units above " + previous + " (Rs per unit): ";
				rates[i] = readDouble(label, 0, 100);
			}
		}
		double fixed = readDouble("Fixed monthly charge in Rs (0 if none): ", 0, 100000);

		try {
			calc.setTariff(new Tariff(limits, rates, fixed));
			lastWhatIf = null; // bills changed, so the old what-if is out of date
			System.out.println("Tariff updated:");
			System.out.print(calc.getTariff().describe());
		} catch (IllegalArgumentException e) {
			System.out.println("Could not update tariff: " + e.getMessage());
		}
	}

	private static int readInt(String prompt, int min, int max) {
		while (true) {
			System.out.print(prompt);
			String line = scanner.nextLine().trim();
			try {
				int value = Integer.parseInt(line);
				if (value >= min && value <= max)
					return value;
				System.out.println("Please enter a number between " + min + " and " + max + ".");
			} catch (NumberFormatException e) {
				System.out.println("That is not a valid whole number. Try again.");
			}
		}
	}

	private static double readDouble(String prompt, double min, double max) {
		while (true) {
			System.out.print(prompt);
			String line = scanner.nextLine().trim();
			try {
				double value = Double.parseDouble(line);
				if (value >= min && value <= max)
					return value;
				System.out.println("Please enter a value between " + min + " and " + max + ".");
			} catch (NumberFormatException e) {
				System.out.println("That is not a valid number. Try again.");
			}
		}
	}

	private static String readText(String prompt) {
		while (true) {
			System.out.print(prompt);
			String line = scanner.nextLine().trim();
			if (!line.isEmpty())
				return line;
			System.out.println("This cannot be empty. Try again.");
		}
	}
}