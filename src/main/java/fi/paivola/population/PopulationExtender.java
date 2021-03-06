package fi.paivola.population;

import au.com.bytecode.opencsv.CSVReader;
import fi.paivola.mapserver.DiagnosticsWrapper;
import fi.paivola.mapserver.core.Event;
import fi.paivola.mapserver.core.DataFrame;
import fi.paivola.mapserver.core.ExtensionModel;
import fi.paivola.mapserver.core.GameManager;
import fi.paivola.mapserver.core.setting.SettingDouble;
import fi.paivola.mapserver.core.setting.SettingInt;
import fi.paivola.mapserver.core.setting.SettingMaster;
import fi.paivola.mapserver.models.ExampleGlobal;
import fi.paivola.mapserver.utils.RangeDouble;
import fi.paivola.mapserver.utils.RangeInt;
import java.io.IOException;
import java.io.InputStreamReader;
import static java.lang.Double.parseDouble;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author hegza
 */
public class PopulationExtender extends ExtensionModel {
    private MortalityModel          mortalityModel;
    private PopulationDistribution  populationByAge;
    private double                  foodShortage;
    private int                     defaultInitialPopulation;
    private double                  defaultBirthPc;
    
    public PopulationExtender(int id) {
        super(id);
        this.mortalityModel = new MortalityModel();
        this.foodShortage = 0;
        this.defaultInitialPopulation = 0;
        this.defaultBirthPc = 0;
    }

    @Override
    public void onTick(DataFrame last, DataFrame current) {
        double population = populationByAge.total() * 1000;
        
        // calculate effects of food shortage
        if (foodShortage > 0) {
            // % of people not fed properly, assuming greedy-distribution
            double severity = foodShortage;
            mortalityModel.setFoodShortage(severity);
        }
        
        // update demographics
        populationByAge.step( 1 );

        // notify amount eaten
        Event consumeFoodEvent = new Event("consumeFood", Event.Type.DOUBLE, population*7);
        addEventTo(parent, current, consumeFoodEvent);
        
        saveData( "totalPopulation", population );
                
        // reset for next frame
        foodShortage = 0;
        mortalityModel.setFoodShortage(0);
    }

    @Override
    public void onEvent(Event e, DataFrame current) {
        if (e.name == "outOfFood") {
            foodShortage = (double)e.value;
        }
    }

    @Override
    public void onRegisteration(GameManager gm, SettingMaster sm) {
        sm.name = "populationExtender";
        sm.exts = "PopCenter";
        // settings / input variables here
        sm.settings.put( "births%", new SettingDouble("Fraction of population "
                + "added as annual births", 0.04, new RangeDouble(0.04, 5.05)) );
        sm.settings.put( "initialPopulation", new SettingInt("Initial "
                + "population in town", 40000, new RangeInt(2000, 200000)));
    }

    @Override
    public void onGenerateDefaults(DataFrame df) {
        // Parse initial age-structure from file
        double[] ageGroups = new double[Constants.NUM_AGE_GROUPS];
        parseInitialAgeStructure(ageGroups, "populationDistribution_2010.csv");
        this.populationByAge = new PopulationDistribution(ageGroups, mortalityModel);

        // 20% of people age 5 years annually 
        populationByAge.setAnnualFlowPc(0.2);
        populationByAge.setBirthsPc( defaultBirthPc );
        
        saveData( "totalPopulation", populationByAge.total()*1000 );
        
        DiagnosticsWrapper.getInstance().println(id+"-totalPopulation:\t"+(long)populationByAge.total());
    }

    @Override
    public void onUpdateSettings(SettingMaster sm) {
        defaultBirthPc = Double.parseDouble(sm.settings.get("births%").getValue());
        defaultInitialPopulation = Integer.parseInt(sm.settings.get("initialPopulation").getValue() );
        saveDouble("births%", defaultBirthPc);
    }
    
    private void parseInitialPopulation(double[] ageGroups, String filename) {
        try {
            CSVReader reader = new CSVReader(new InputStreamReader(PopulationExtender.class.getClassLoader().getResourceAsStream(filename)));
            String[] nextLine;
            int line = 0;
            while ((nextLine = reader.readNext()) != null) {
                if (line > 0) {
                    for (int i = 0; i != Constants.NUM_AGE_GROUPS; ++i) {
                        ageGroups[i] = parseDouble(nextLine[i]);
                    }
                }
                line++;
            }
        } catch (IOException ex) {
            Logger.getLogger(ExampleGlobal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void parseInitialAgeStructure(double[] ageGroups, String filename) {
        double totalPopulation = (double)(defaultInitialPopulation/1000); // divided by 1000 to save precision
        try {
            CSVReader reader = new CSVReader(new InputStreamReader(PopulationExtender.class.getClassLoader().getResourceAsStream(filename)));
            String[] nextLine;
            int line = 0;
            while ((nextLine = reader.readNext()) != null) {
                if (line > 0) {
                    for (int i = 0; i != Constants.NUM_AGE_GROUPS; ++i) {
                        ageGroups[i] = parseDouble(nextLine[i])*totalPopulation;
                    }
                }
                line++;
            }
        } catch (IOException ex) {
            Logger.getLogger(ExampleGlobal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
