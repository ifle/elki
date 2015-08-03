package de.lmu.ifi.dbs.elki.data.uncertain.probabilitydensityfunction;
/*
This file is part of ELKI:
Environment for Developing KDD-Applications Supported by Index-Structures

Copyright (C) 2015
Ludwig-Maximilians-Universität München
Lehr- und Forschungseinheit für Datenbanksysteme
ELKI Development Team

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.lmu.ifi.dbs.elki.algorithm.clustering.uncertain.PWCClusteringAlgorithm;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.spatial.SpatialComparable;
import de.lmu.ifi.dbs.elki.data.uncertain.ContinuousUncertainObject;
import de.lmu.ifi.dbs.elki.data.uncertain.UncertainObject;
import de.lmu.ifi.dbs.elki.data.uncertain.UncertainUtil;
import de.lmu.ifi.dbs.elki.datasource.filter.typeconversions.UncertainifyFilter;
import de.lmu.ifi.dbs.elki.math.random.RandomFactory;
import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.NumberArrayAdapter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.RandomParameter;

/**
 * ProbabilityDensityFunction class to model dimensional independent gaussian
 * distributions.
 *
 * Used for construction of {@link UncertainObject}, filtering with
 * {@link UncertainifyFilter} and sampling with {@link PWCClusteringAlgorithm}.
 *
 * @author Alexander Koos
 */
public class IndependentGaussianDistributionFunction extends AbstractGaussianDistributionFunction<DoubleVector, IndependentGaussianDistributionFunction> {
  /**
   * Constructor.
   *
   * @param minDev
   * @param maxDev
   * @param minMin
   * @param maxMin
   * @param minMax
   * @param maxMax
   * @param multMin
   * @param multMax
   * @param rand
   */
  public IndependentGaussianDistributionFunction(double minDev, double maxDev, double minMin, double maxMin, double minMax, double maxMax, int multMin, int multMax, Random rand) {
    this.minDev = minDev;
    this.maxDev = maxDev;
    this.minMin = minMin;
    this.maxMin = maxMin;
    this.minMax = minMax;
    this.maxMax = maxMax;
    this.multMin = multMin;
    this.multMax = multMax;
    this.urand = rand;
  }

  /**
   * Constructor.
   *
   * @param means
   * @param variances
   */
  public IndependentGaussianDistributionFunction(List<DoubleVector> means, List<DoubleVector> variances) {
    this(means, variances, null);
  }

  /**
   * Constructor.
   *
   * @param means
   * @param variances
   * @param weights
   */
  public IndependentGaussianDistributionFunction(List<DoubleVector> means, List<DoubleVector> variances, int[] weights) {
    if(means.size() != variances.size() || (weights != null && variances.size() != weights.length)) {
      throw new IllegalArgumentException("[W: ]\tSize of 'means' and 'variances' has to be the same, also Dimensionality of weights.");
    }
    for(int i = 0; i < means.size(); i++) {
      if(variances.get(i).getDimensionality() != means.get(i).getDimensionality()) {
        throw new IllegalArgumentException("[W: ]\tDimensionality of contained DoubleVectors for 'means' and 'variances' hast to be the same.");
      }
    }
    if(weights == null) {
      this.weights = new int[means.size()];
      final int c = weightMax / means.size();
      for(int i = 0; i < means.size(); i++) {
        this.weights[i] = c;
      }
    }
    else {
      this.weights = weights;
      this.weightMax = 0;
      for(int i = 0; i < weights.length; i++) {
        this.weightMax += weights[i];
      }
    }

    this.means = means;
    this.variances = variances;
  }

  @Override
  public DoubleVector drawValue(SpatialComparable bounds, Random rand) {
    int index = 0;
    final double[] values = new double[bounds.getDimensionality()];

    for(int j = 0; j < UncertainObject.DEFAULT_TRY_LIMIT; j++) {
      if(weights.length > 1) {
        index = UncertainUtil.drawIndexFromIntegerWeights(rand, weights, weightMax);
      }
      boolean inBounds = index < weights.length;
      if(!inBounds) {
        continue;
      }
      for(int i = 0; i < values.length; i++) {
        values[i] = means.get(index).doubleValue(i) + rand.nextGaussian() * variances.get(index).doubleValue(i);
        inBounds &= values[i] <= bounds.getMax(i) && values[i] >= bounds.getMin(i);
      }
      if(inBounds) {
        return new DoubleVector(values);
      }
    }

    return AbstractGaussianDistributionFunction.noSample;
  }

  @Override
  public DoubleVector getMean(SpatialComparable bounds) {
    double[] meanVals = new double[bounds.getDimensionality()];
    int sumWeights = 0;

    for(int i = 0; i < means.size(); i++) {
      for(int j = 0; j < bounds.getDimensionality(); j++) {
        meanVals[j] += means.get(i).getValues()[j] * weights[i];
        sumWeights += weights[i];
      }
    }

    if(sumWeights > 0) {
      for(int j = 0; j < bounds.getDimensionality(); j++) {
        meanVals[j] /= sumWeights;
      }
    }

    return new DoubleVector(meanVals);
  }

  @Override
  protected List<DoubleVector> getDeviationVector() {
    return this.variances;
  }

  @Override
  public <A> ContinuousUncertainObject<IndependentGaussianDistributionFunction> uncertainify(A array, NumberArrayAdapter<?, A> adapter, boolean blur) {
    final int dim = adapter.size(array);
    final int multiplicity = urand.nextInt((multMax - multMin) + 1) + multMin;
    final List<DoubleVector> means = new ArrayList<DoubleVector>();
    final List<DoubleVector> variances = new ArrayList<DoubleVector>();
    int[] weights;
    weights = UncertainUtil.calculateRandomIntegerWeights(multiplicity, weightMax, urand);
    for(int h = 0; h < multiplicity; h++) {
      final double[] imeans = new double[dim];
      final double[] ivariances = new double[dim];
      final double minBound = (urand.nextDouble() * (maxMin - minMin)) + minMin;
      final double maxBound = (urand.nextDouble() * (maxMax - minMax)) + minMax;
      for(int i = 0; i < dim; i++) {
        ivariances[i] = (urand.nextDouble() * (maxDev - minDev)) + minDev;
        final double vi = adapter.getDouble(array, i);
        if(blur) {
          for(int j = 0; j < UncertainObject.DEFAULT_TRY_LIMIT; j++) {
            final double val = this.urand.nextGaussian() * ivariances[i] + vi;
            if(val >= vi - minBound && val <= vi + maxBound) {
              imeans[i] = val;
              break;
            }
          }
          if(imeans[i] == 0.0 && (imeans[i] < vi - (minBound * ivariances[i]) || imeans[i] > vi + (maxBound * ivariances[i]))) {
            imeans[i] = this.urand.nextInt(2) == 1 ? vi - (minBound * ivariances[i]) : vi + (maxBound * ivariances[i]);
          }
        }
        else {
          imeans[i] = vi;
        }
      }
      means.add(new DoubleVector(imeans));
      variances.add(new DoubleVector(ivariances));
    }
    return new ContinuousUncertainObject<>(new IndependentGaussianDistributionFunction(means, variances, weights), dim);
  }

  /**
   * Parameterizer class.
   *
   * @author Alexander Koos
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Field to hold parameter value.
     */
    protected double stddevMin;

    /**
     * Field to hold parameter value.
     */
    protected double stddevMax;

    /**
     * Field to hold parameter value.
     */
    protected double minMin;

    /**
     * Field to hold parameter value.
     */
    protected double maxMin;

    /**
     * Field to hold parameter value.
     */
    protected double minMax;

    /**
     * Field to hold parameter value.
     */
    protected double maxMax;

    /**
     * Field to hold parameter value.
     */
    protected int multMin;

    /**
     * Field to hold parameter value.
     */
    protected int multMax;

    /**
     * Field to hold RandomFactory for creation of Random.
     */
    protected RandomFactory randFac;

    /**
     * Parameter to specify the minimum randomly drawn variances shall have.
     */
    public final static OptionID STDDEV_MIN_ID = new OptionID("uo.stddev.min", "Minimum variance to be used.");

    /**
     * Parameter to specify the maximum randomly drawn variances shall have.
     */
    public final static OptionID STDDEV_MAX_ID = new OptionID("uo.stddev.max", "Maximum variance to be used.");

    /**
     * Parameter to specify the minimum value for randomly drawn deviation from
     * the groundtruth in negative direction.
     */
    public final static OptionID MIN_MIN_ID = new OptionID("uo.lbound.min", "Minimum deviation for lower boundary - boundary calculates: mean - lbound");

    /**
     * Parameter to specify the maximum value for randomly drawn deviation from
     * the groundtruth in negative direction.
     */
    public final static OptionID MAX_MIN_ID = new OptionID("uo.lbound.max", "Maximum deviation for lower boundary - see uo.lbound.min");

    /**
     * Parameter to specify the minimum value for randomly drawn deviation from
     * the groundtruth in positive direction.
     */
    public final static OptionID MIN_MAX_ID = new OptionID("uo.ubound.min", "Minimum deviation for upper boundary - boundary calculates: mean + ubound");

    /**
     * Parameter to specify the maximum value for randomly drawn deviation from
     * the groundtruth in positive direction.
     */
    public final static OptionID MAX_MAX_ID = new OptionID("uo.ubound.max", "Maximum deviation for upper boundary - see uo.ubound.min");

    /**
     * Parameter to specify the minimum value for randomly drawn multiplicity of
     * an uncertain object, i.e. how many gaussian distributions are hold.
     */
    public final static OptionID MULT_MIN_ID = new OptionID("uo.mult.min", "Minimum amount of possible gaussian distributions per uncertain object.");

    /**
     * Parameter to specify the maximum value for randomly drawn multiplicity of
     * an uncertain object, i.e. how many gaussian distributions are hold.
     */
    public final static OptionID MULT_MAX_ID = new OptionID("uo.mult.max", "Maximum amount of possible gaussian distributions per uncertain object.");

    /**
     * Parameter to seed the Random used for uncertainification.
     */
    public final static OptionID SEED_ID = new OptionID("uo.seed", "Seed used for uncertainification.");

    @Override
    protected void makeOptions(final Parameterization config) {
      super.makeOptions(config);
      final DoubleParameter pDevMin = new DoubleParameter(Parameterizer.STDDEV_MIN_ID, UncertainObject.DEFAULT_STDDEV);
      if(config.grab(pDevMin)) {
        this.stddevMin = pDevMin.getValue();
      }
      final DoubleParameter pDevMax = new DoubleParameter(Parameterizer.STDDEV_MAX_ID, UncertainObject.DEFAULT_STDDEV);
      if(config.grab(pDevMax)) {
        this.stddevMax = pDevMax.getValue();
      }
      final DoubleParameter pMinMin = new DoubleParameter(Parameterizer.MIN_MIN_ID, UncertainObject.DEFAULT_MIN_MAX_DEVIATION_GAUSSIAN);
      if(config.grab(pMinMin)) {
        this.minMin = pMinMin.getValue();
      }
      final DoubleParameter pMaxMin = new DoubleParameter(Parameterizer.MAX_MIN_ID, UncertainObject.DEFAULT_MIN_MAX_DEVIATION_GAUSSIAN);
      if(config.grab(pMaxMin)) {
        this.maxMin = pMaxMin.getValue();
      }
      final DoubleParameter pMinMax = new DoubleParameter(Parameterizer.MIN_MAX_ID, UncertainObject.DEFAULT_MIN_MAX_DEVIATION_GAUSSIAN);
      if(config.grab(pMinMax)) {
        this.minMax = pMinMax.getValue();
      }
      final DoubleParameter pMaxMax = new DoubleParameter(Parameterizer.MAX_MAX_ID, UncertainObject.DEFAULT_MIN_MAX_DEVIATION_GAUSSIAN);
      if(config.grab(pMaxMax)) {
        this.maxMax = pMaxMax.getValue();
      }
      final IntParameter pMultMin = new IntParameter(Parameterizer.MULT_MIN_ID, UncertainObject.DEFAULT_MULTIPLICITY);
      if(config.grab(pMultMin)) {
        this.multMin = pMultMin.getValue();
      }
      final IntParameter pMultMax = new IntParameter(Parameterizer.MULT_MAX_ID, UncertainObject.DEFAULT_MULTIPLICITY);
      if(config.grab(pMultMax)) {
        this.multMax = pMultMax.getValue();
      }
      final RandomParameter pseed = new RandomParameter(Parameterizer.SEED_ID);
      if(config.grab(pseed)) {
        this.randFac = pseed.getValue();
      }
    }

    @Override
    protected Object makeInstance() {
      return new IndependentGaussianDistributionFunction(stddevMin, stddevMax, minMin, maxMin, minMax, maxMax, multMin, multMax, randFac.getRandom());
    }
  }
}
