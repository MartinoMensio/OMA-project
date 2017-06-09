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
			/*
			The soft constraint adds penalties for solutions that we don't like.
			It is used as a cost calculator instead of the default one.
			The iterations will select the solution with lower cost, so applying penalties will make those solutions less attractive.
			*/
			final StateManager stateManager = new StateManager(vrp);
			ConstraintManager constraintManager = new ConstraintManager(vrp, stateManager);

			SolutionCostCalculator costCalculator = new SolutionCostCalculator() {
				@Override
				public double getCosts(VehicleRoutingProblemSolution solution) {
					// initialize the cost to 0
					double costs = 0.;
					// for each route compute the cost (standard cost)
					for (VehicleRoute route : solution.getRoutes()) {
						// add the cost of the vehicle
						costs += route.getVehicle().getType().getVehicleCostParams().fix;
						// add the cost of the route
						costs += stateManager.getRouteState(route, InternalStates.COSTS, Double.class);
					}
					int nRoutes = solution.getRoutes().size();
					// penalty for unused routes
					// the penalty is proportional to how much the used number of vehicle is distant from the desired
					// the multiplication costant is selected to make this a big penalty
					costs += (MyUtil.numVehiclesToUse - nRoutes) * 1000000;
					// penalty for unassigned jobs
					costs += solution.getUnassignedJobs().size() * 1000000;
					// among all the routes, find the minimum number of jobs for a single route
					int minJobsInRoute = solution.getRoutes().stream().mapToInt(getRouteActivitiesSize).min().getAsInt();
					// among all the routes, find the maximum number of jobs for a single route
					int maxJobsInRoute = solution.getRoutes().stream().mapToInt(getRouteActivitiesSize).max().getAsInt();
					// calculate the ratio between the two found values (this is a measure of unbalancedness)
					double jobsRate = ((double)maxJobsInRoute) / minJobsInRoute;
					// penalty for unbalanced routes
					if (MyUtil.wantBalanced && jobsRate > MyUtil.balanceFactor) {
						// if this ratio is too high, apply penalty
						// this penalty is smaller than the one applied for wrong number of vehicles or for unassigned jobs
						// because the balance is a less important factor
						costs += 100000 * jobsRate;
					}
					return costs;
				}
			};
			// HARD constraint in insertion
			/*
			This is applied in a insertionContext, building the solution. It does not allow some solutions to be built
			*/
			HardRouteConstraint routeLevelConstraint = new HardRouteConstraint() {
				// limits for the number of jobs per tour.
				// Both are near the value MyUtil.nJobs/ (MyUtil.numVehiclesToUse - 1) that could allow an equal division of the jobs without using a vehicle.
				// if few vehicles, the constraint is the floor of this value because we don't want to pass over this value
				private int maxJobPerTourLowN = (int)Math.floor(((double)MyUtil.nJobs) / (MyUtil.numVehiclesToUse - 1));
				// if more vehicles, we should give a higher margin of freedom, because if more vehicles are used, less jobs are served by each one of them
				private int maxJobPerTourHighN = (int)Math.ceil(((double)MyUtil.nJobs) / (MyUtil.numVehiclesToUse - 1)) + 1;
				// those limits do not provide a general solution to the problem. They have been found by doing some experiments and could be defined better.


				// This function is evaluated in order to understand if an insertion can be done on a certain route
				@Override
				public boolean fulfilled(JobInsertionContext insertionContext) {
					int maxJobPerTour;
					// apply our naif criteria
					if (MyUtil.numVehiclesToUse > 9) {
						maxJobPerTour = maxJobPerTourHighN;
					} else {
						maxJobPerTour = maxJobPerTourLowN;
					}
					// if our criteria is not satisfied (already reached the limit on number of jobs for this tour)
					if (insertionContext.getRoute().getTourActivities().getActivities().size() >= maxJobPerTour) {
						// forbid the insertion
						return false;
					}
					return true;
				}
			};
			

			// START
			VehicleRoutingAlgorithmBuilder vraBuilder = new VehicleRoutingAlgorithmBuilder(vrp,
					(String) options.get(CONSTANTS.CONFIG));
			// these two lines add the hard constraint
			constraintManager.addConstraint(routeLevelConstraint);
			vraBuilder.setStateAndConstraintManager(stateManager, constraintManager);

			vraBuilder.addCoreConstraints();
			vraBuilder.addDefaultCostCalculators();
			vraBuilder.setNuOfThreads((int) options.get(CONSTANTS.THREADS));
			// this line subtitutes the default cost calculator, linking the soft constraint
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
