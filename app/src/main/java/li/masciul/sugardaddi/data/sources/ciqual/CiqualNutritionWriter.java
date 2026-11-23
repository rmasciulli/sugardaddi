package li.masciul.sugardaddi.data.sources.ciqual;

import androidx.annotation.NonNull;

import li.masciul.sugardaddi.core.models.Nutrition;

/**
 * CiqualNutritionWriter - writes one Ciqual value into a Nutrition object.
 *
 * Used by both Ciqual paths (the downloaded dataset and the live search API),
 * after CiqualConstituents found the constituent by code or by name: a nutrient
 * lands in the same field, in the same unit, whichever path brought it.
 */
public final class CiqualNutritionWriter {

    private CiqualNutritionWriter() { /* no instances */ }

    /**
     * @param n           the Nutrition being filled
     * @param constituent the Ciqual constituent (see CiqualConstituents)
     * @param ciqualValue the value in Ciqual's unit (see CiqualConstituents.parseValue)
     */
    public static void write(@NonNull Nutrition n,
                             @NonNull CiqualConstituents.Constituent constituent,
                             double ciqualValue) {
        Double v = constituent.toAppUnit(ciqualValue);
        switch (constituent.field) {
            case ENERGY_KJ:           n.setEnergyKj(v);           break;
            case ENERGY_KCAL:         n.setEnergyKcal(v);         break;
            case WATER:               n.setWater(v);              break;
            case ASH:                 n.setAsh(v);                break;
            case SALT:                n.setSalt(v);               break;
            case SODIUM:              n.setSodium(v);             break;
            case MAGNESIUM:           n.setMagnesium(v);          break;
            case PHOSPHORUS:          n.setPhosphorus(v);         break;
            case CHLORIDE:            n.setChloride(v);           break;
            case POTASSIUM:           n.setPotassium(v);          break;
            case CALCIUM:             n.setCalcium(v);            break;
            case MANGANESE:           n.setManganese(v);          break;
            case IRON:                n.setIron(v);               break;
            case COPPER:              n.setCopper(v);             break;
            case ZINC:                n.setZinc(v);               break;
            case SELENIUM:            n.setSelenium(v);           break;
            case IODINE:              n.setIodine(v);             break;
            case PROTEINS:            n.setProteins(v);           break;
            case CARBOHYDRATES:       n.setCarbohydrates(v);      break;
            case SUGARS:              n.setSugars(v);             break;
            case FRUCTOSE:            n.setFructose(v);           break;
            case GALACTOSE:           n.setGalactose(v);          break;
            case GLUCOSE:             n.setGlucose(v);            break;
            case LACTOSE:             n.setLactose(v);            break;
            case MALTOSE:             n.setMaltose(v);            break;
            case SUCROSE:             n.setSucrose(v);            break;
            case STARCH:              n.setStarch(v);             break;
            case POLYOLS:             n.setPolyols(v);            break;
            case FIBER:               n.setFiber(v);              break;
            case FAT:                 n.setFat(v);                break;
            case SATURATED_FAT:       n.setSaturatedFat(v);       break;
            case MONOUNSATURATED_FAT: n.setMonounsaturatedFat(v); break;
            case POLYUNSATURATED_FAT: n.setPolyunsaturatedFat(v); break;
            case BUTYRIC_ACID:        n.setButyricAcid(v);        break;
            case CAPROIC_ACID:        n.setCaproicAcid(v);        break;
            case CAPRYLIC_ACID:       n.setCaprylicAcid(v);       break;
            case CAPRIC_ACID:         n.setCapricAcid(v);         break;
            case LAURIC_ACID:         n.setLauricAcid(v);         break;
            case MYRISTIC_ACID:       n.setMyristicAcid(v);       break;
            case PALMITIC_ACID:       n.setPalmiticAcid(v);       break;
            case STEARIC_ACID:        n.setStearicAcid(v);        break;
            case LINOLEIC_ACID:       n.setLinoleicAcid(v);       break;
            case ALA:                 n.setALA(v);                break;
            case ARACHIDONIC_ACID:    n.setArachidonicAcid(v);    break;
            case EPA:                 n.setEPA(v);                break;
            case DHA:                 n.setDHA(v);                break;
            case VITAMIN_A:           n.setVitaminA(v);           break;
            case RETINOL:             n.setRetinol(v);            break;
            case BETA_CAROTENE:       n.setBetaCarotene(v);       break;
            case VITAMIN_D:           n.setVitaminD(v);           break;
            case VITAMIN_D2:          n.setVitaminD2(v);          break;
            case VITAMIN_D3:          n.setVitaminD3(v);          break;
            case VITAMIN_E:           n.setVitaminE(v);           break;
            case VITAMIN_K1:          n.setVitaminK1(v);          break;
            case VITAMIN_K2:          n.setVitaminK2(v);          break;
            case VITAMIN_C:           n.setVitaminC(v);           break;
            case VITAMIN_B1:          n.setVitaminB1(v);          break;
            case VITAMIN_B2:          n.setVitaminB2(v);          break;
            case VITAMIN_B3:          n.setVitaminB3(v);          break;
            case VITAMIN_B5:          n.setVitaminB5(v);          break;
            case VITAMIN_B6:          n.setVitaminB6(v);          break;
            case VITAMIN_B12:         n.setVitaminB12(v);         break;
            case VITAMIN_B9:          n.setVitaminB9(v);          break;
            case INTRINSIC_FOLATE:    n.setIntrinsicFolate(v);    break;
            case FOLIC_ACID:          n.setFolicAcid(v);          break;
            case ALCOHOL:             n.setAlcohol(v);            break;
            case ORGANIC_ACIDS:       n.setOrganicAcids(v);       break;
            case CHOLESTEROL:         n.setCholesterol(v);        break;
        }
    }
}
