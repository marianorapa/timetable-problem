import java.util.*;
import java.io.*;

/*
  TODO
  - New crossover with point
  - Keep bad individuals to next generation
  - Do not eliminate all bad time tables but letting some join the crossoever
  - When selecting crossoever parents, use the random wheel

        // TODO: optimize the check of double bookings by only iterating over
      // the roomtimetables only once,
      // fast functions are important to GA, since they are slow

      // TODO: a lab or class for a course should result in several events
      // when checking if a studentgroup is double booked
      // it should be allowed to have a studentgroup id double booked
      // if the double bookings are class or lab
*/

/**
 * Performs the Genetic Algorithm(GA) on the KTH data set.
 */
public class GA {

  public enum SELECTION_TYPE {
    NORMAL,
    ROULETTE_WHEEL,
    TOURNAMENT;

    public static String[] getNames() {
      GA.SELECTION_TYPE[] states = values();
      String[] names = new String[states.length];
      for (int i = 0; i < states.length; i++) {
          names[i] = states[i].name();
      }
      return names;
    }
  };

  public enum MUTATION_TYPE {
    NORMAL;

    public static String[] getNames() {
      GA.MUTATION_TYPE[] states = values();
      String[] names = new String[states.length];
      for (int i = 0; i < states.length; i++) {
          names[i] = states[i].name();
      }
      return names;
    }
  };

  // algorithm parameters
  private int DESIRED_FITNESS;
  private int MAX_POPULATION_SIZE;
  private int MUTATION_PROBABILITY; // compared with 1000
  private int CROSSOVER_PROBABILITY; // compared with 1000
  private int SELECTION_SIZE;
  private int TOURNAMENT_POOL_SIZE = 5;
  private SELECTION_TYPE selectionType = SELECTION_TYPE.NORMAL;
  private MUTATION_TYPE mutationType = MUTATION_TYPE.NORMAL;

  private Population population;
  private KTH kth;

  public GA() {
    kth = new KTH();
  }

  // only use this with the test input
  public void testConstraints() {
    loadData("../input/TestConstraintsInput");
    
    // create one timetable manually
    TimeTable tt = new TimeTable(2);
    
    Room r1 = kth.getRooms().get(0);
    Room r2 = kth.getRooms().get(1);

    RoomTimeTable rtt1 = new RoomTimeTable(r1);
    RoomTimeTable rtt2 = new RoomTimeTable(r2);

    tt.putRoomTimeTable(0, rtt1);
    tt.putRoomTimeTable(1, rtt2);
    
    // events, ids start at 1
    // there are 8 events with this input
    Event lectureEvent = kth.getEvents().get(1);

    Event lessonEvent1 = kth.getEvents().get(2);
    Event lessonEvent2 = kth.getEvents().get(3);
    Event lessonEvent3 = kth.getEvents().get(4);
    
    Event labEvent1 = kth.getEvents().get(5);
    Event labEvent2 = kth.getEvents().get(6);
    Event labEvent3 = kth.getEvents().get(7);
    Event labEvent4 = kth.getEvents().get(8);

    // book the events

    // lecture in too small lecture hall, 1 capacity error
    rtt2.setEvent(0, 0, lectureEvent.getId());

    // student group double booked with the lecture, 1 sg double booking
    rtt1.setEvent(0, 0, lessonEvent1.getId());
    
    // two lessons "double booked" for a student group
    // should not give sg double booking error
    rtt2.setEvent(0, 1, lessonEvent2.getId());
    rtt1.setEvent(0, 1, lessonEvent3.getId());
    
    rtt2.setEvent(4, 0, labEvent1.getId());
    rtt2.setEvent(4, 1, labEvent2.getId());
    rtt2.setEvent(4, 2, labEvent3.getId());
    rtt2.setEvent(4, 3, labEvent4.getId());

    // expected: 
    // 1 sg double booking
    // 0 lecturer double bookings, since only one lecture
    // 1 capacity breach, lecture in too small lecture hall
    // 7 type breaches, only lecture halls and 7 labs/lessons events

    System.out.println("Studentgroup doublebookings: " + studentGroupDoubleBookings2(tt));
    System.out.println("Lecturer doublebookings: " + lecturerDoubleBookings(tt));
    System.out.println("Room capacity breaches: " + roomCapacityBreaches(tt));
    System.out.println("Room type breaches: " + roomTypeBreaches(tt));
  }

  public static void main(String[] args) {
    (new GA()).testConstraints();
  }

  /*
  * Returns a schedule based on the given constraints
  */
  public TimeTable generateTimeTable() {
    // run until the fitness is high enough
    // high enough should at least mean that
    // all hard constraints are solved
    // adjust for the number of soft constraints to be solved too
    // use another stop criteria too, in order to not run forever?

    // create the initial random population
    createRandomPopulation();
    ListIterator<TimeTable> it = population.listIterator();
    while(it.hasNext()) {
      TimeTable tt = it.next();
      fitness(tt);
    }

    int numGenerations = 1;
    while (population.getTopIndividual().getFitness() < DESIRED_FITNESS) {
      // select the population used for the crossover
      population = selection(population); 
      
      // add new individuals to the population using crossover
      population = breed(population); 
      
      // sort the population by their fitness
      population.sortIndividuals(); 
      
      numGenerations++;
      System.out.println("#GENERATIONS: " + numGenerations + " BEST FITNESS: " + population.getTopIndividual().getFitness());
    }
    return population.getTopIndividual();
  }


  //////////////////////////
  // SETUP
  //////////////////////////

  public void loadData(String dataFileUrl) {
    kth.clear(); // reset all previous data before loading

    try {
      File file = new File(dataFileUrl);
      BufferedReader in = new BufferedReader(new FileReader(file));
      String line = null;
      // input data sections are read in the following order separated by #
      // #rooms <name> <capacity> <type>
      // #courses <id> <name> <numLectures> <numClasses> <numLabs>
      // #lecturers <name> <course>+
      // #studentgroups <name> <numStudents> <course>+
      String readingSection = null;
      String roomName = null;
      String courseName = null;
      String lecturerName = null;
      String studentGroupName = null;
      HashMap<String, Integer> courseNameToId = new HashMap<String, Integer>();
      while((line = in.readLine()) != null) {
        String[] data = line.split(" ");
        if(data[0].charAt(0) == '#') {
          readingSection = data[1];
          data = in.readLine().split(" ");
        }
        if(readingSection.equals("ROOMS")) {
          roomName = data[0];
          int cap = Integer.parseInt(data[1]);
          Event.Type type = Event.generateType(Integer.parseInt(data[2]));
          Room room = new Room(roomName, cap, type);
          kth.addRoom(room);
        } else if(readingSection.equals("COURSES")) {
          courseName = data[0];
          int numLectures = Integer.parseInt(data[1]);
          int numLessons = Integer.parseInt(data[2]);
          int numLabs = Integer.parseInt(data[3]);
          Course course = new Course(courseName, numLectures, numLessons, numLabs);
          courseNameToId.put(courseName, course.getId());
          kth.addCourse(course);
        } else if(readingSection.equals("LECTURERS")) {
          lecturerName = data[0];
          Lecturer lecturer = new Lecturer(lecturerName);
          for(int i = 1; i < data.length; i++) {
            // register all courses that this lecturer may teach
            courseName = data[i];
            lecturer.addCourse(kth.getCourses().get(courseNameToId.get(courseName)));
          }
          kth.addLecturer(lecturer);
        } else if(readingSection.equals("STUDENTGROUPS")) {
          studentGroupName = data[0];
          int size = Integer.parseInt(data[1]);
          StudentGroup studentGroup = new StudentGroup(studentGroupName, size);
          for(int i = 2; i < data.length; i++) {
            courseName = data[i];
            studentGroup.addCourse(kth.getCourses().get(courseNameToId.get(courseName)));
          }
          kth.addStudentGroup(studentGroup);
        }
      }
      kth.createEvents(); // create all events
      in.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void loadConstraints(String constraintsFileUrl) {
    // TODO read constraints from a file
  }

  //////////////////////////
  // GENETIC ALGORITHMS
  //////////////////////////

  private Population createRandomPopulation() {
    population = new Population();
    population.createRandomIndividuals(MAX_POPULATION_SIZE, kth);
    return population;
  }

  private Population selection(Population population) {
    switch(selectionType) {
      case ROULETTE_WHEEL:
        //population.sortIndividuals();
        return rouletteWheelSelection(population);
      case TOURNAMENT:
        //population.sortIndividuals();
        return tournamentSelection(population);
      case NORMAL:
        population.sortIndividuals();
        return cullPopulation(population);
      default:
        break;
    }
    return null;
  }

  private Population rouletteWheelSelection(Population population) {
    Population selection = new Population();
    ListIterator<TimeTable> it = population.listIterator();

    /*
    double fitnessSum = 0.0d;
    double fitnessSums[] = new double[population.size()];
    fitnessSums[0] = it.next().getFitness();

    int i = 1;
    while(it.hasNext()) {
      double fitness = it.next().getFitness();
      fitnessSums[i] = fitnessSums[i-1] + fitness;
      i++;
    }

    Random rand = new Random(System.currentTimeMillis());
    for(int j = 0; j < SELECTION_SIZE; j++) {
      double randomFitness = rand.nextDouble() * fitnessSums[fitnessSums.length-1];
      int index = Arrays.binarySearch(fitnessSums, randomFitness);
      // adjust negative insertion points
      if(index < 0) {
        index = Math.abs(index+1);
      } if (index >= MAX_POPULATION_SIZE) {
        index = MAX_POPULATION_SIZE - 1;
      }
      selection.addIndividual(population.getIndividual(index));
    }
    */

    int fitnessSum = 0;
    while(it.hasNext()) {
      fitnessSum += it.next().getFitness();
    }
    // randomize fitness value deciding which individuals to select
    Random rand = new Random(System.currentTimeMillis());
    for (int i = 0; i < SELECTION_SIZE; i++) {
      it = population.listIterator();
      TimeTable tt = it.next();
      int randomFitness = -1 * rand.nextInt(-1 * fitnessSum);
      int currentFitness = tt.getFitness();
      while(currentFitness >= randomFitness) {
        tt = it.next();
        currentFitness += tt.getFitness();
      }
      fitnessSum -= tt.getFitness();
      selection.addIndividual(tt);
      it.remove();
    }
    return selection;
  }

  private Population tournamentSelection(Population population) {
    Population selection = new Population();
    ListIterator<TimeTable> it = population.listIterator();

    Random rand = new Random(System.currentTimeMillis());
    List<Integer> poolIndices = new ArrayList<Integer>();
    for (int i = 0; i < MAX_POPULATION_SIZE; i++) {
      poolIndices.add(i);
    }

    while(selection.size() < SELECTION_SIZE) {
      Population pool = new Population();
      Collections.shuffle(poolIndices);
      int i = 0;
      while(pool.size() < TOURNAMENT_POOL_SIZE) {
        pool.addIndividual(population.getIndividual(poolIndices.get(i)));
      }
      pool.sortIndividuals();
      selection.addIndividual(pool.getTopIndividual());
    }

    /*
    while (selection.size() < SELECTION_SIZE) {
      Collections.shuffle(poolIndices);
      int cId1 = poolIndices.get(0);
      int cId2 = poolIndices.get(1);
      TimeTable c1 = population.getIndividual(cId1);
      TimeTable c2 = population.getIndividual(cId2);
      int winnerId = cId1;
      TimeTable winner = c1;
      if(c2.getFitness() > c1.getFitness()) {
        winnerId = cId2;
        winner = c2;
      }
      poolIndices.remove((Integer)winnerId);
      selection.addIndividual(winner);
    }
    */

      /*
      Population pool = new Population();
      for(int i = 0; i < TOURNAMENT_POOL_SIZE; i++) {
        int id = poolIndices.get(i);
        TimeTable tt = population.getIndividual(id);
        pool.addIndividual(tt);
      }
      pool.sortIndividuals();
      selection.addIndividual(pool.getBestIndividual());
      poolIndices.remove(new Integer(pool.getBestIndividual().getId()));
      */
    return selection;
  }

  private Population cullPopulation(Population population) {
    Population culledPopulation = new Population();
    ListIterator<TimeTable> it = population.listIterator();

    // take the top half of population
    // assumes individuals are sorted
    Random rand = new Random();
    for (int i = 0; i < SELECTION_SIZE; i++) {
      TimeTable tt = it.next();
      culledPopulation.addIndividual(tt);
    }
    return culledPopulation;
  }

  // implement different selection/crossover algorithms here
  // mutate according to mutation rate
  private Population breed(Population population) {
    Random rand = new Random(System.currentTimeMillis());
    List<Integer> parentIndices = new ArrayList<Integer>();

    for (int i = 0; i < SELECTION_SIZE; i++) {
      parentIndices.add(i);
    }

    // breed until the population is restored to its normal size
    while (population.size() < MAX_POPULATION_SIZE) {
      // pick two parents randomly among the population
      Collections.shuffle(parentIndices);
      int p1 = parentIndices.get(0);
      int p2 = parentIndices.get(1);
      TimeTable t1 = population.getIndividual(p1);
      TimeTable t2 = population.getIndividual(p2);
      //TimeTable child = crossover(t1, t2);
      TimeTable child = crossoverWithPoint(t1, t2);
      //TimeTable child = crossoverWithTwoPoints(t1, t2);
			
      //fitness(child);
      /*
      if(child.getFitness() < t1.getFitness() && child.getFitness() < t2.getFitness()) {
        mutate(child);
        repairTimeTable(child);
      } else {
              mutate(child);
        repairTimeTable(child);
      }
      */
      mutate(child);
      repairTimeTable(child);
      fitness(child);
      population.addIndividual(child);
    }
    return population;
  }

  // For each gene (booking in a timeslot), take with equal
  // probability from either parent
  private TimeTable crossover(TimeTable t1, TimeTable t2) {
    TimeTable child = new TimeTable(kth.getNumRooms());

    RoomTimeTable[] rtts1 = t1.getRoomTimeTables();
    RoomTimeTable[] rtts2 = t2.getRoomTimeTables();

    Random rand = new Random(System.currentTimeMillis());

    // for every roomtimetable
    for (int i = 0; i < kth.getNumRooms(); i++) {
      RoomTimeTable rtt = new RoomTimeTable(rtts1[i].getRoom());

      // for each available time
      for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                            timeslot++) {
        for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
          int allele;
          if (rand.nextBoolean()) {
            // take from parent 1
            allele = rtts1[i].getEvent(day, timeslot);

          } else {
            // take from parent 2
            allele = rtts2[i].getEvent(day, timeslot);
          }

          rtt.setEvent(day, timeslot, allele);
        }
      }

      child.putRoomTimeTable(i, rtt);
    }

    return child;
  }

	// only one point now
  private TimeTable crossoverWithPoint(TimeTable t1, TimeTable t2) {
		TimeTable child = new TimeTable(kth.getNumRooms());

		int interval = kth.getNumRooms() * RoomTimeTable.NUM_TIMESLOTS *
																			 RoomTimeTable.NUM_DAYS;

		int point = new Random(System.currentTimeMillis()).nextInt(interval);

		RoomTimeTable[] rtts1 = t1.getRoomTimeTables();
		RoomTimeTable[] rtts2 = t2.getRoomTimeTables();

		int gene = 0;

		// iterate over the genes
		for (int i = 0; i < kth.getNumRooms(); i++) {
			RoomTimeTable rtt = new RoomTimeTable(rtts1[i].getRoom());

			// for each available time
			for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
																											timeslot++) {
				for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
					int allele;

					if (gene < point) {
						allele = rtts1[i].getEvent(day, timeslot);
					} else {
						allele = rtts2[i].getEvent(day, timeslot);
					}

					rtt.setEvent(day, timeslot, allele);
					gene++;
				}
			}

			child.putRoomTimeTable(i, rtt);
		}

    return child;
  }

  private TimeTable crossoverWithTwoPoints(TimeTable t1, TimeTable t2) {
    // TODO: should be two children?
    TimeTable child = new TimeTable(kth.getNumRooms());

		int interval = kth.getNumRooms() * RoomTimeTable.NUM_TIMESLOTS *
																			 RoomTimeTable.NUM_DAYS;

    Random rand = new Random(System.currentTimeMillis());
		int p1 = rand.nextInt(interval);
    int p2 = rand.nextInt(interval);

    if (p1 > p2) {
      // swap them
      int tmp = p2;
      p2 = p1;
      p1 = tmp;
    }

		RoomTimeTable[] rtts1 = t1.getRoomTimeTables();
		RoomTimeTable[] rtts2 = t2.getRoomTimeTables();

		int gene = 0;

		// iterate over the genes
		for (int i = 0; i < kth.getNumRooms(); i++) {
			RoomTimeTable rtt = new RoomTimeTable(rtts1[i].getRoom());

			// for each available time
			for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
																											timeslot++) {
				for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
          int allele;

          if (gene > p1 && gene < p2) {
            allele = rtts2[i].getEvent(day, timeslot);
          
          } else {
            allele = rtts1[i].getEvent(day, timeslot);
          }

          rtt.setEvent(day, timeslot, allele);

          gene++;
        }
      }

      child.putRoomTimeTable(i, rtt);
    }

    return child;
  }

	// TODO: add arithmetic and heuristic crossover too?

  // TODO: write a crossover function that takes half from parent 1
  // then scans parent2 and adds genevalue if it isnt added yet

  private void repairTimeTable(TimeTable tt) {
    HashMap<Integer, LinkedList<RoomDayTime>> locations = new HashMap<Integer,
                                                    LinkedList<RoomDayTime>>();

    LinkedList<RoomDayTime> unusedSlots = new LinkedList<RoomDayTime>();

    // initiate number of bookings to 0
    for (int eventID : kth.getEvents().keySet()) {
      locations.put(eventID, new LinkedList<RoomDayTime>());
    }

    RoomTimeTable[] rtts = tt.getRoomTimeTables();

    for (int i = 0; i < kth.getNumRooms(); i++) {
      RoomTimeTable rtt = rtts[i];
      // for each available time
      for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                     timeslot++) {
        for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
          int bookedEvent = rtt.getEvent(day, timeslot);
          if (bookedEvent == 0) {
            // add to usable slots
            unusedSlots.add(new RoomDayTime(i, day, timeslot));

          } else {
            // save the location
            locations.get(bookedEvent).add(new RoomDayTime(i, day, timeslot));
          }
        }
      }
    }

    List<Integer> unbookedEvents = new LinkedList<Integer>();

    for (int eventID : kth.getEvents().keySet()) {
      if (locations.get(eventID).size() == 0) {
        // this event is unbooked
        unbookedEvents.add(eventID);

      } else if (locations.get(eventID).size() > 1) {
        // this is event is booked more than once
        // randomly make those slots unused until only one remains
        LinkedList<RoomDayTime> slots = locations.get(eventID);
        Collections.shuffle(slots);

        // TODO: this could probably lead to infinite loops if input
        // data is bad
        while (slots.size() > 1) {
          RoomDayTime rdt = slots.removeFirst();

          // mark this slot as unused
          unusedSlots.add(rdt);
          rtts[rdt.room].setEvent(rdt.day, rdt.time, 0);
        }
      }
    }

    // now put each unbooked event in an unused slot
    Collections.shuffle(unusedSlots);
    for (int eventID : unbookedEvents) {
      RoomDayTime rdt = unusedSlots.removeFirst();
      rtts[rdt.room].setEvent(rdt.day, rdt.time, eventID);
    }
  }

  // wrapper class only used in repair function
  private class RoomDayTime {
    int room;
    int day;
    int time;

    RoomDayTime(int room, int day, int time) {
      this.room = room;
      this.day = day;
      this.time = time;
    }
  }

  //////////////////////////
  // MUTATION
  //////////////////////////

  private void mutate(TimeTable tt) {
    //mutateRandomGene(tt);
    mutateSwapGene(tt);
  }

  private void mutateRandomGene(TimeTable tt) {
    Random rand = new Random(System.currentTimeMillis());
    RoomTimeTable[] rtts = tt.getRoomTimeTables();

    for (int i = 0; i < kth.getNumRooms(); i++) {
      RoomTimeTable rtt = rtts[i];

      // for each available time
      for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                            timeslot++) {
        for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
          if (rand.nextInt(1000) < MUTATION_PROBABILITY) {
            // mutate this gene
            int allele = kth.getRandomEventId(rand);
            rtt.setEvent(day, timeslot, allele);
          }
        }
      }
    }
  }

  private void mutateSwapGene(TimeTable tt) {
    Random rand = new Random(System.currentTimeMillis());
    RoomTimeTable[] rtts = tt.getRoomTimeTables();

    for (int i = 0; i < kth.getNumRooms(); i++) {
      RoomTimeTable rtt = rtts[i];
      // for each available time
      for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                            timeslot++) {
        for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
          if (rand.nextInt(1000) < MUTATION_PROBABILITY) {
            // mutate this gene
            int swapTargetDay = rand.nextInt(RoomTimeTable.NUM_DAYS);
            int swapTargetTimeslot = rand.nextInt(RoomTimeTable.NUM_TIMESLOTS);
            int swapTargetEventId = rtt.getEvent(swapTargetDay, swapTargetTimeslot);
            int swapSrcEventId = rtt.getEvent(day, timeslot);
            rtt.setEvent(swapTargetDay, swapTargetTimeslot, swapSrcEventId);
            rtt.setEvent(day, timeslot, swapTargetEventId);
          }
        }
      }
    }
  }

  //////////////////////////
  // FITNESS
  //////////////////////////

  // Idea for fitness:
  // Each of the softconstraints met should give a positive value
  // Each of the hard constraints that are not met should give a negative value
  // Each hard constraints negative contribution should be higher than the
  // highest possible sum of the positive contributions of the soft constraints
  // A working schedule is then a schedule with positive fitness
  // A higher fitness is more desirable
  private void fitness(TimeTable tt) {
    // set the fitness to this time table
    //int studentGroupDoubleBookings = studentGroupDoubleBookings(tt);
    int studentGroupDoubleBookings = studentGroupDoubleBookings2(tt);
    
    int lecturerDoubleBookings = lecturerDoubleBookings(tt);
    int roomCapacityBreaches = roomCapacityBreaches(tt);
    int roomTypeBreaches = roomTypeBreaches(tt);

    int numBreaches = studentGroupDoubleBookings * 2+
                      lecturerDoubleBookings +
                      roomCapacityBreaches * 4 +
                      roomTypeBreaches * 4;

    /* DEBUG
    System.out.println("=============================================");
    System.out.println(
      studentGroupDoubleBookings + " " +
      lecturerDoubleBookings + " " +
      roomCapacityBreaches + " " +
      roomTypeBreaches
      );
    */
    
    // TODO: add in the soft constraints
    // TODO weight the different constraints breaches
    int fitness = -1 * numBreaches;
    tt.setFitness(fitness);
  }

  //////////////////////////
  // CONSTRAINTS
  //////////////////////////

  ///////////////////
  // Hard constraints, each function returns the number of constraint breaches
  ///////////////////

  // NOTE: Two of the hard constraints are solved by the chosen datastructure
  // Invalid timeslots may not be used
  // A room can not be double booked at a certain timeslot

  // TODO: better name please
  // probably not needed
  private int unbookedEvents(TimeTable tt) {
    return 0;
  }

  // TODO: will this be needed?
  private int eventDoubleBooked(TimeTable tt) {
    return 0;
  }
  
  // TODO: This version is the working one, remove the other one
  // num times a studentgroup is double booked
  // OPTIMIZE: just iterate over the rooms once instead?
  private int studentGroupDoubleBookings2(TimeTable tt) {
    int numBreaches = 0;

    RoomTimeTable[] rtts = tt.getRoomTimeTables();

    for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                        timeslot++) {
      for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
        for (StudentGroup sg : kth.getStudentGroups().values()) {
          
          // TODO: use array instead of map
          // the array should be of size equal to the total
          // amount of eventgroups and needs to be reset for the next 
          // studentgroup
          HashMap<Integer, Integer> eventGroupCounts = 
          new HashMap<Integer, Integer>();

          for (RoomTimeTable rtt : rtts) {
            int eventID = rtt.getEvent(day, timeslot);

            // only look at booked timeslots
            if (eventID != 0) {
              Event event = kth.getEvent(eventID);
              int sgID = event.getStudentGroup().getId();
              
              // if this bookings is for the current studentgroup
              if (sgID == sg.getId()) {
                // TODO: räkna antalet av alla eventgroupids?
                // antag att den som har flest är "rätt"
                // resten är då fel
                
                int eventGroupID = event.getEventGroupId();
                
                // increment the count for this event group id
                if (!eventGroupCounts.containsKey(eventGroupID)) {
                  eventGroupCounts.put(eventGroupID, 1);
                
                } else {
                  int oldCount = eventGroupCounts.get(eventGroupID);
                  eventGroupCounts.put(eventGroupID, oldCount + 1);
                }
              }
            }
          }
          
          // find the biggest event group
          int biggestGroup; 
          int biggestGroupSize = 0;
          int sumGroupSize = 0;
          for (Map.Entry<Integer, Integer> entry : 
                                  eventGroupCounts.entrySet()) {
            
            sumGroupSize += entry.getValue();

            if (entry.getValue() > biggestGroupSize) {
              biggestGroup = entry.getKey();
              biggestGroupSize = entry.getValue();
            }
          }

          numBreaches += sumGroupSize - biggestGroupSize;
        }
      }
    }

    return numBreaches;
  }
  
  // TODO: This version should be removed, the problem is solved
  // in the version above
  // TODO: with this code it is possible for a course
  // to have many of its lessons or labs at the same time
  // possible fix: group events created from the same lesson/lab
  // with an id or something
  private int studentGroupDoubleBookings(TimeTable tt) {
    int numBreaches = 0;

    RoomTimeTable[] rtts = tt.getRoomTimeTables();

    for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                        timeslot++) {
      for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
        for (StudentGroup sg : kth.getStudentGroups().values()) {
          int numLectures = 0;
          int numLessons = 0;
          int numLabs = 0;

          for (RoomTimeTable rtt : rtts) {
            int eventID = rtt.getEvent(day, timeslot);

            // only look at booked timeslots
            if (eventID != 0) {
              Event event = kth.getEvent(eventID);
              int sgID = event.getStudentGroup().getId();

              if (sgID == sg.getId()) {
                Event.Type eventType = event.getType();
                if (eventType == Event.Type.LECTURE) {
                  numLectures++;

                } else if (eventType == Event.Type.LESSON) {
                  numLessons++;

                } else {
                  // lab
                  numLabs++;
                }
              }
            }
          }

          int max = max(numLectures, numLessons, numLabs);

          if (max == numLessons) {
            numBreaches += numLectures + numLabs;

          } else if (max == numLabs) {
            numBreaches += numLectures + numLessons;

          } else {
            // max is numLectures
            if (numLessons > 0 || numLabs > 0) {
              if (numLessons > numLabs) {
                numBreaches += numLectures + numLabs;
              } else {
                numBreaches += numLectures + numLessons;
              }

            } else {
              numBreaches += numLectures - 1;
            }
          }
        }
      }
    }

    return numBreaches;
  }

  private int max(int a, int b, int c) {
    int max = a;

    if (b > max) {
      max = b;
    }

    if (c > max) {
      max = c;
    }

    return max;
  }

  // num times a lecturer is double booked
  // NOTE: lecturers are only booked to lectures
  // for the labs and classes, TAs are used and they are assumed to always
  // be available
  private int lecturerDoubleBookings(TimeTable tt) {
    int numBreaches = 0;

    RoomTimeTable[] rtts = tt.getRoomTimeTables();

    for (Lecturer lecturer : kth.getLecturers().values()) {

      // for each time
      for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                           timeslot++) {

        for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
          int numBookings = 0;

          for (RoomTimeTable rtt : rtts) {
            int eventID = rtt.getEvent(day, timeslot);

            // 0 is unbooked
            if (eventID != 0) {
              Event event = kth.getEvent(eventID);
              // only check lectures since lecturers are only
              // attached to lecture events
              if (event.getType() == Event.Type.LECTURE) {
                if (event.getLecturer().getId() == lecturer.getId()) {
                  numBookings++;
                }
              }
            }
          }

          // only one booking per time is allowed
          if (numBookings > 1) {

            // add all extra bookings to the number of constraint breaches
            numBreaches += numBookings - 1;
          }
        }
      }
    }

    return numBreaches;
  }

  // num times a room is too small for the event booked
  private int roomCapacityBreaches(TimeTable tt) {
    int numBreaches = 0;

    RoomTimeTable[] rtts = tt.getRoomTimeTables();

    for (RoomTimeTable rtt : rtts) {
      int roomSize = rtt.getRoom().getCapacity();

      // for each time
      for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                          timeslot++) {

        for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
          int eventID = rtt.getEvent(day, timeslot);

          // only look at booked timeslots
          if (eventID != 0) {
            int eventSize = kth.getEvent(eventID).getSize();
            if (roomSize < eventSize) {
              numBreaches++;
            }
          }
        }
      }
    }

    return numBreaches;
  }

  // num times an event is booked to the wrong room type
  private int roomTypeBreaches(TimeTable tt) {
    int numBreaches = 0;

    RoomTimeTable[] rtts = tt.getRoomTimeTables();

    for (RoomTimeTable rtt : rtts) {
      Event.Type roomType = rtt.getRoom().getType();

      // for each time
      for (int timeslot = 0; timeslot < RoomTimeTable.NUM_TIMESLOTS;
                                                          timeslot++) {

        for (int day = 0; day < RoomTimeTable.NUM_DAYS; day++) {
          int eventID = rtt.getEvent(day, timeslot);

          // only look at booked timeslots
          if (eventID != 0) {
            Event.Type type = kth.getEvent(eventID).getType();
            if (roomType != type) {
              numBreaches++;
            }
          }
        }
      }
    }

    return numBreaches;
  }

  ///////////////////
  // Soft constraints, each function returns the number of constraint breaches
  ///////////////////

  // TODO: how to represent the soft constraints?
  // This is harder to represent

  // timetables with rooms closer together should be given
  // higher scores
  private double distanceBetweenRoomsStudentGroup(TimeTable tt) {
    return 0.0;
  }

  // timetables with rooms closer together should be given
  // higher scores
  private double distanceBetweenRoomsLecturer(TimeTable tt) {
    return 0.0;
  }

  // should schedules be "tightly" packed?
  private double unusedTimeSlots() {
    // räkna antal håltimmar?
    return 0.0;
  }

  public void setMutationProbability(int p) {
    MUTATION_PROBABILITY = p;
  }

  public void setCrossoverProbability(int p) {
    CROSSOVER_PROBABILITY = p;
  }

  public void setPopulationSize(int size) {
    MAX_POPULATION_SIZE = size;
  }

  public void setSelectionSize(int size) {
    SELECTION_SIZE = size;
  }

  public void setMutationType(int i) {
    mutationType = MUTATION_TYPE.values()[i];
  }

  public void setSelectionType(int i) {
    selectionType = SELECTION_TYPE.values()[i];
  }

  // print the given time table in a readable format
  public void printTimeTable(TimeTable tt) {
    kth.printTimeTable(tt);
  }

  public void printConf() {
    System.out.println("Desired fitness: " + DESIRED_FITNESS);
    System.out.println("Population size: " + MAX_POPULATION_SIZE);
    System.out.println("Selection size: " + SELECTION_SIZE);
    System.out.println("Mutation type: " + mutationType);
    System.out.println("P(Mutation) = " + ((double)MUTATION_PROBABILITY / 1000.0d) + "%");
    System.out.println("Selection type: " + selectionType);
    System.out.println("P(Crossover) = " + ((double)CROSSOVER_PROBABILITY / 1000.0d) + "%");
  }
}
