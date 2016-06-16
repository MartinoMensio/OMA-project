/*******************************************************************************
 * Copyright (C) 2014  Stefan Schroeder
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package jsprit.instance.reader;

import jsprit.core.problem.Location;
import jsprit.core.problem.VehicleRoutingProblem;
import jsprit.core.problem.VehicleRoutingProblem.FleetSize;
import jsprit.core.problem.job.Service;
import jsprit.core.problem.solution.route.activity.TimeWindow;
import jsprit.core.problem.vehicle.VehicleImpl;
import jsprit.core.problem.vehicle.VehicleTypeImpl;
import jsprit.core.util.Coordinate;
import main.MyUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

/**
 * Reader that reads the well-known solomon-instances.
 * 
 * <p>
 * See: <a href=
 * "http://neo.lcc.uma.es/vrp/vrp-instances/capacitated-vrp-with-time-windows-instances/">
 * neo.org</a>
 * 
 * @author stefan
 *
 */

public class SolomonReader {

	/**
	 * @param costProjectionFactor
	 *            the costProjectionFactor to set
	 */
	public void setVariableCostProjectionFactor(double costProjectionFactor) {
		this.variableCostProjectionFactor = costProjectionFactor;
	}

	private static Logger logger = LogManager.getLogger(SolomonReader.class);

	private final VehicleRoutingProblem.Builder vrpBuilder;

	private double coordProjectionFactor = 1;

	private double timeProjectionFactor = 1;

	private double variableCostProjectionFactor = 1;

	private double fixedCostPerVehicle = 0.0;

	public SolomonReader(VehicleRoutingProblem.Builder vrpBuilder) {
		super();
		this.vrpBuilder = vrpBuilder;
	}

	public SolomonReader(VehicleRoutingProblem.Builder vrpBuilder, double fixedCostPerVehicle) {
		super();
		this.vrpBuilder = vrpBuilder;
		this.fixedCostPerVehicle = fixedCostPerVehicle;
	}

	public void read(String solomonFile) {
		// double coefficientVariation=MyUtil.coefficientVariation;


		vrpBuilder.setFleetSize(FleetSize.FINITE);
		// vrpBuilder.setFleetSize(FleetSize.INFINITE);
		// FIXME
		// READ FROM FILE START
		HashMap<String, Integer> solomonFiles = new HashMap<>();

		BufferedReader br;

		try {
			br = new BufferedReader(new FileReader("input/instanceVehicles.txt"));
			String solomonFileRow = br.readLine();
			String[] solomonFileRows = solomonFileRow.split(",");
			solomonFiles.put(solomonFileRows[0].toLowerCase(), Integer.parseInt(solomonFileRows[1]));
			while (solomonFileRow != null) {
				solomonFileRow = br.readLine();
				solomonFileRows = solomonFileRow.split(",");
				solomonFiles.put(solomonFileRows[0].toLowerCase(), Integer.parseInt(solomonFileRows[1]));
			}

			br.close();
		} catch (Exception e) {
		}

		// END READ FROM FILE

		/*
		 * HashMap<String,Integer> solomonFiles= new HashMap<>();
		 * solomonFiles.put("input/c101.txt",10);
		 * solomonFiles.put("input/c102.txt",10);
		 * solomonFiles.put("input/c103.txt",10);
		 * solomonFiles.put("input/c104.txt",10);
		 * solomonFiles.put("input/c105.txt",10);
		 * solomonFiles.put("input/c106.txt",10);
		 * solomonFiles.put("input/c107.txt",10);
		 * solomonFiles.put("input/c108.txt",10);
		 * solomonFiles.put("input/c109.txt",10);
		 * solomonFiles.put("input/c201.txt",3);
		 * solomonFiles.put("input/c202.txt",3);
		 * solomonFiles.put("input/c203.txt",3);
		 * solomonFiles.put("input/c204.txt",3);
		 * solomonFiles.put("input/c205.txt",3);
		 * solomonFiles.put("input/c206.txt",3);
		 * solomonFiles.put("input/c207.txt",3);
		 * solomonFiles.put("input/c208.txt",3);
		 * solomonFiles.put("input/rc101.txt",15);
		 * solomonFiles.put("input/rc102.txt",14);
		 * solomonFiles.put("input/rc103.txt",12); //11
		 * solomonFiles.put("input/rc104.txt",10);
		 * solomonFiles.put("input/rc105.txt",15);
		 * solomonFiles.put("input/rc106.txt",13);
		 * solomonFiles.put("input/rc107.txt",12);
		 * solomonFiles.put("input/rc108.txt",11);
		 * solomonFiles.put("input/rc201.txt",8); //7
		 * solomonFiles.put("input/rc202.txt",7); //6
		 * solomonFiles.put("input/rc203.txt",5); //4
		 * solomonFiles.put("input/rc204.txt",4); //3
		 * solomonFiles.put("input/rc205.txt",7); //6
		 * solomonFiles.put("input/rc206.txt",5); //4
		 * solomonFiles.put("input/rc207.txt",5);
		 * solomonFiles.put("input/rc208.txt",4); //3
		 */
		BufferedReader reader = getReader(solomonFile);
		int vehicleCapacity = 0;
		int memorizedNumber = solomonFiles.get(solomonFile.toLowerCase());
		MyUtil.numVehiclesToUse = (int) Math.ceil(memorizedNumber * MyUtil.coefficientVariation);
		// MARTINO read first time to calculate capacity
		// no longer used: soft constraint instead
		//

		int vvv=0;
		int counter = 0; 
		String line;
		int totalDemand = 0;
		int maxDemand = 0;
		MyUtil.nJobs = 0;
		while((line = readLine(reader)) != null){ 
			line = line.replace("\r", ""); 
			line = line.trim(); 
			String[] tokens = line.split(" +"); 
			counter++;
			if(counter == 5){ 
				vehicleCapacity = Integer.parseInt(tokens[1]);
				continue; 
			} if(counter > 9){ 
				if(tokens.length < 7) 
					continue; 
				int demand = Integer.parseInt(tokens[3]); 
				totalDemand+=demand; if (demand> maxDemand) { 
					maxDemand = demand; 
				}
				MyUtil.nJobs++;
			} 
		}

		int reducedvehicleCapacity = (int)Math.ceil(totalDemand / (MyUtil.numVehiclesToUse - 0.9)) + maxDemand; 
		if (reducedvehicleCapacity < vehicleCapacity) { 
			// UNCOMMENT this to get balanced routes 
			//FIXME
			//vehicleCapacity = reducedvehicleCapacity;
		} try {
			reader.close(); 
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace(); 
		}

		reader = getReader(solomonFile);
		vvv = 0;
		counter = 0;
		//String line;

		while ((line = readLine(reader)) != null) {
			line = line.replace("\r", "");
			line = line.trim();
			String[] tokens = line.split(" +");
			counter++;
			if (counter == 5) {
				//vehicleCapacity = Integer.parseInt(tokens[1]);
				// vehicleCapacity = 90;
				// vehicleCapacity = (int) (vehicleCapacity /
				// coefficientVariation);
				continue;
			}
			if (counter > 9) {
				if (tokens.length < 7)
					continue;
				Coordinate coord = makeCoord(tokens[1], tokens[2]);
				String customerId = tokens[0];
				int demand = Integer.parseInt(tokens[3]);
				// totalDemand+=demand;
				double start = Double.parseDouble(tokens[4]) * timeProjectionFactor;
				double end = Double.parseDouble(tokens[5]) * timeProjectionFactor;
				double serviceTime = Double.parseDouble(tokens[6]) * timeProjectionFactor;
				if (counter == 10) {
					// num vehicles

					for (int i = 0; i < MyUtil.numVehiclesToUse; i++) {
						VehicleTypeImpl.Builder typeBuilder = VehicleTypeImpl.Builder.newInstance("solomonType")
								.addCapacityDimension(0, vehicleCapacity);
						typeBuilder.setCostPerDistance(1.0 * variableCostProjectionFactor)
								.setFixedCost(fixedCostPerVehicle);
						VehicleTypeImpl vehicleType = typeBuilder.build();

						VehicleImpl vehicle = VehicleImpl.Builder.newInstance("solomonVehicle" + i)
								.setEarliestStart(start).setLatestArrival(end)
								.setStartLocation(
										Location.Builder.newInstance().setId(customerId).setCoordinate(coord).build())
								.setType(vehicleType).build();
						vrpBuilder.addVehicle(vehicle);
						// vrpBuilder.addVehicle(vehicle);
						// VehicleRoutingProblemSolution
						vvv++;
					}
				} else {
					Service service = Service.Builder.newInstance(customerId).addSizeDimension(0, demand)
							.setLocation(Location.Builder.newInstance().setCoordinate(coord).setId(customerId).build())
							.setServiceTime(serviceTime).setTimeWindow(TimeWindow.newInstance(start, end)).build();
					vrpBuilder.addJob(service);
				}
			}
		}
		close(reader);
	}

	public void setCoordProjectionFactor(double coordProjectionFactor) {
		this.coordProjectionFactor = coordProjectionFactor;
	}

	private void close(BufferedReader reader) {
		try {
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("Exception:", e);
			System.exit(1);
		}
	}

	private String readLine(BufferedReader reader) {
		try {
			return reader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("Exception:", e);
			System.exit(1);
			return null;
		}
	}

	private Coordinate makeCoord(String xString, String yString) {
		double x = Double.parseDouble(xString);
		double y = Double.parseDouble(yString);
		return new Coordinate(x * coordProjectionFactor, y * coordProjectionFactor);
	}

	private BufferedReader getReader(String solomonFile) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(solomonFile));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			logger.error("Exception:", e1);
			System.exit(1);
		}
		return reader;
	}

	public void setTimeProjectionFactor(double timeProjection) {
		this.timeProjectionFactor = timeProjection;

	}
}
