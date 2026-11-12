# Electricity Consumption and Savings Calculator

A Java web application that calculates a household's monthly electricity consumption and bill based on its appliances. It provides usage breakdowns, savings suggestions with estimated rupee savings, what-if comparisons, and seasonal electricity consumption estimates.

## Features

### 1. Electricity Consumption Calculator
- Add appliances (name, watts, hours per day, quantity).
- Calculate monthly electricity consumption in units.
- Calculate electricity bills using editable slab rates and a fixed charge.
- Display appliance-wise usage breakdown.
- Show a slab-by-slab bill calculation.

### 2. Savings Analyzer
- Suggest ways to reduce electricity consumption.
- Rank savings suggestions based on estimated rupees saved.
- Display the potential savings for each suggestion.

### 3. What-If Comparison
- Compare electricity bills before and after changing appliance usage.
- Estimate the savings from proposed changes.
- Perform comparisons without modifying the original appliance list.

### 4. Seasonal Consumption Estimator
- Select a season: Summer, Winter, or Rainy.
- Adjust appliance usage hours according to predefined seasonal patterns.
- Calculate daily and monthly electricity consumption for each appliance.
- Estimate the total household monthly consumption.
- Calculate estimated monthly electricity bills using the existing slab tariff.
- Compare electricity consumption and estimated bills across all three seasons.
- Display appliance-wise consumption and seasonal differences.

### 5. Downloadable Report
- Generate and download a text report containing electricity consumption and bill details.

## How to Run

You need **JDK 17 or later**.

Run all commands from the project folder (the folder containing `src` and `web`).

### Using Eclipse

1. Select File → New → Java Project.
2. Untick "Use default location" and browse to this project folder.
3. Untick "Create module-info.java".
4. Right-click `src/electricity/WebApp.java`.
5. Select Run As → Java Application.

### Using a Terminal

Compile the Java files:

```bash
javac -d out src/electricity/*.java
```

Start the web application:

```bash
java -cp out electricity.WebApp
```

Open the local URL displayed in the terminal in your browser.

The default address is:

http://localhost:8080/

If that port is already occupied, the application may start on another available port.

To stop the application, press Ctrl+C in the terminal or use the Stop button in Eclipse.

### Console Version

To run the console-based application:

```bash
java -cp out electricity.Main
```

## Project Structure

| Part | What it does |
|---|---|
| `Appliance` | Represents an appliance and calculates its monthly units. |
| `Tariff` | Stores slab rates and performs bill calculations. |
| `BillCalculator` | Manages appliances, total units, and electricity bills. |
| `SavingsAnalyzer` | Analyses consumption and generates savings suggestions. |
| `Suggestion` | Represents an electricity-saving suggestion. |
| `WhatIfResult` | Stores the results of what-if comparisons. |
| `ReportWriter` | Generates downloadable text reports. |
| `SeasonalConsumptionEstimator` | Estimates and compares electricity consumption across different seasons. |
| `WebApp` | Provides the web server and connects Java classes to the frontend. |
| `web/index.html` | Defines the web application's structure. |
| `web/style.css` | Handles the application's styling. |
| `web/app.js` | Handles user interactions and displays results. |

## Technologies Used

- **Java 17** – Backend calculations and application logic.
- **HTML** – Web page structure.
- **CSS** – User interface styling.
- **JavaScript** – Frontend interactions and communication with the backend.
- **Java HTTP Server** – Handles web requests and connects the frontend with Java classes.

## Working on This Project

- Create a separate Git branch for each new feature or change.
- Pull the latest changes before starting development.
- Test your changes locally before committing.
- Commit and push your changes to your branch.
- Open a Pull Request for review and merging into the main branch.

## Note

The slab rates in `Tariff()` are sample values. Use your state's actual electricity tariff for a real bill.

The Seasonal Consumption Estimator uses predefined seasonal appliance usage patterns. Its results are estimates and may differ from actual household consumption.
