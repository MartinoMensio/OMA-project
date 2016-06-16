/*******************************************************************************
 * Copyright (C) 2015  ORO e ISMB
 * Questo e' il main del programma di ottimizzazione VRPTW
 * Il programma prende in input un file csv con i clienti ed un csv di configurazione (deposito e veicoli) e restituisce in output il file delle route ed un file sintetico di dati dei viaggi.
 * L'algoritmo ultizzato e' un Large Neighborhood 
 ******************************************************************************/

package main;

import java.util.Collection;
import java.util.function.ToIntFunction;

import jsprit.analysis.toolbox.GraphStreamViewer;
import jsprit.analysis.toolbox.GraphStreamViewer.Label;
import jsprit.core.algorithm.VehicleRoutingAlgorithm;
import jsprit.core.algorithm.VehicleRoutingAlgorithmBuilder;
import jsprit.core.algorithm.selector.SelectBest;
import jsprit.core.algorithm.state.InternalStates;
import jsprit.core.algorithm.state.StateManager;
import jsprit.core.algorithm.termination.TimeTermination;
import jsprit.core.problem.VehicleRoutingProblem;
import jsprit.core.problem.constraint.ConstraintManager;
import jsprit.core.problem.constraint.HardRouteConstraint;
import jsprit.core.problem.misc.JobInsertionContext;
import jsprit.core.problem.solution.SolutionCostCalculator;
import jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import jsprit.core.problem.solution.route.VehicleRoute;
import jsprit.core.reporting.SolutionPrinter;
import jsprit.instance.reader.SolomonReader;
import jsprit.util.*;
import main.OROoptions.CONSTANTS;
import main.OROoptions.PARAMS;

public class Main {

	public static void main(String[] args) {

		// save coeff to file and read it from solomonreader
		double coefficientVariation = 1.0;
		boolean wantBalanced = false;
		// default value if user specifies that wants balanced
		// without specifying rate (max/min)
		double balanceFactor = 2;
		try {
			coefficientVariation = Double.parseDouble(args[2]);
			// if user wants a balanced solution
			if (args.length >= 4 && args[3].equals("b")) {
				wantBalanced = true;
			}
			// if user specifies rate of balance (not compulsory)
			if (args.length >= 5) {
				balanceFactor = Double.parseDouble(args[4]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		MyUtil.coefficientVariation = coefficientVariation;
		MyUtil.wantBalanced = wantBalanced;
		MyUtil.balanceFactor = balanceFactor;
		String[] backupArgs = { args[0], args[1] };
		args = backupArgs;

		// Some preparation - create output folder
		Examples.createOutputFolder();

		// Read input parameters
		OROoptions options = new OROoptions(args);

		for (int r = 0; r < (int) options.get(CONSTANTS.REPETITION); r++) {
			// Time tracking
			long startTime = System.currentTimeMillis();
			// Create a vrp problem builder
			VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
			// FIXME
			// vrpBuilder.setFleetSize(FleetSize.FINITE);

			// FIXME

			// A solomonReader reads solomon-instance files, and stores the
			// required information in the builder.
			new SolomonReader(vrpBuilder).read("input/" + options.get(PARAMS.INSTANCE));
			VehicleRoutingProblem vrp = vrpBuilder.build();
			// Create the instace and solve the problem

			// SOFT CONSTRAINT
			final StateManager stateManager = new StateManager(vrp);
			ConstraintManager constraintManager = new ConstraintManager(vrp, stateManager);

			SolutionCostCalculator costCalculator = new SolutionCostCalculator() {
				@Override
				public double getCosts(VehicleRoutingProblemSolution solution) {
					double costs = 0.;
					for (VehicleRoute route : solution.getRoutes()) {
						costs += route.getVehicle().getType().getVehicleCostParams().fix;
						costs += stateManager.getRouteState(route, InternalStates.COSTS, Double.class);
					}
					int nRoutes = solution.getRoutes().size();
					// penalty for unused routes
					costs += (MyUtil.numVehiclesToUse - nRoutes) * 1000000;
					// penalty for unassigned jobs
					costs += solution.getUnassignedJobs().size() * 1000000;
					int minJobsInRoute = solution.getRoutes().stream().mapToInt(getRouteActivitiesSize).min().getAsInt();
					int maxJobsInRoute = solution.getRoutes().stream().mapToInt(getRouteActivitiesSize).max().getAsInt();
					double jobsRate = ((double)maxJobsInRoute) / minJobsInRoute;
					// penalty for unbalanced routes
					if (MyUtil.wantBalanced && jobsRate > MyUtil.balanceFactor) {
						costs += 100000 * jobsRate;
					}
					return costs;
				}
			};
			// HARD constraint in insertion
			HardRouteConstraint routeLevelConstraint = new HardRouteConstraint() {
				private int maxJobPerTourLowN = (int)Math.floor(((double)MyUtil.nJobs) / (MyUtil.numVehiclesToUse - 1));
				private int maxJobPerTourHighN = (int)Math.ceil(((double)MyUtil.nJobs) / (MyUtil.numVehiclesToUse - 1)) + 1;
				
				@Override
				public boolean fulfilled(JobInsertionContext insertionContext) {
					int maxJobPerTour;
					if (MyUtil.numVehiclesToUse > 9) {
						maxJobPerTour = maxJobPerTourHighN;
					} else {
						maxJobPerTour = maxJobPerTourLowN;
					}
					if (insertionContext.getRoute().getTourActivities().getActivities().size() >= maxJobPerTour) {
						return false;
					}
					return true;
				}
			};
			

			// START
			VehicleRoutingAlgorithmBuilder vraBuilder = new VehicleRoutingAlgorithmBuilder(vrp,
					(String) options.get(CONSTANTS.CONFIG));
			constraintManager.addConstraint(routeLevelConstraint);
			vraBuilder.setStateAndConstraintManager(stateManager, constraintManager);
			vraBuilder.addCoreConstraints();
			vraBuilder.addDefaultCostCalculators();
			vraBuilder.setNuOfThreads((int) options.get(CONSTANTS.THREADS));
			vraBuilder.setObjectiveFunction(costCalculator);
			VehicleRoutingAlgorithm vra = vraBuilder.build();
			// END

			// VehicleRoutingAlgorithm vra =
			// VehicleRoutingAlgorithms.readAndCreateAlgorithm(vrp,
			// (int)options.get(CONSTANTS.THREADS),
			// (String)options.get(CONSTANTS.CONFIG));
			setTimeLimit(vra, (long) options.get(CONSTANTS.TIME));

			// Solve the problem
			Collection<VehicleRoutingProblemSolution> solutions = vra.searchSolutions();
			// Extract the best solution

			// FIXME
			// instead of best, select the one that uses all vehicles
			VehicleRoutingProblemSolution solution = new SelectBest().selectSolution(solutions);

			// Print solution on a file
			OROutils.write(solution, (String) options.get(PARAMS.INSTANCE), System.currentTimeMillis() - startTime,
					(String) options.get(CONSTANTS.OUTPUT));
			// Print solution on the screen (optional)
			SolutionPrinter.print(vrp, solution, SolutionPrinter.Print.CONCISE);
			// Draw solution on the screen (optional)
			//new GraphStreamViewer(vrp,solution).labelWith(Label.ID).setRenderDelay(10).display();

		}

	}

	private static void setTimeLimit(VehicleRoutingAlgorithm vra, long timeMilliSec) {
		TimeTermination tterm = new TimeTermination(timeMilliSec);
		vra.setPrematureAlgorithmTermination(tterm);
		vra.addListener(tterm);
	}
	
	private static ToIntFunction<VehicleRoute> getRouteActivitiesSize = new ToIntFunction<VehicleRoute>() {

		@Override
		public int applyAsInt(VehicleRoute route) {
			return route.getActivities().size();
		}

	};
}
