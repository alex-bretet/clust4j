package com.clust4j.algo;

import java.util.ArrayList;
import java.util.TreeMap;

import org.apache.commons.math3.linear.AbstractRealMatrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;

import com.clust4j.GlobalState;
import com.clust4j.metrics.SilhouetteScore;
import com.clust4j.metrics.UnsupervisedIndexAffinity;
import com.clust4j.utils.GeometricallySeparable;
import com.clust4j.utils.MatUtils;
import com.clust4j.utils.ModelNotFitException;
import com.clust4j.utils.VecUtils;

public abstract class AbstractCentroidClusterer extends AbstractPartitionalClusterer 
		implements CentroidLearner, Convergeable, UnsupervisedClassifier {
	
	private static final long serialVersionUID = -424476075361612324L;
	final public static double DEF_TOLERANCE = 0.005;
	final public static int DEF_K = Neighbors.DEF_K;
	
	final protected int maxIter;
	final protected double tolerance;
	final protected int[] init_centroid_indices;
	final protected int m;
	
	volatile protected boolean converged = false;
	volatile protected double tssCost = Double.NaN;
	volatile protected int[] labels = null;
	volatile protected int iter = 0;
	
	/** Key is the group label, value is the corresponding centroid */
	volatile protected ArrayList<double[]> centroids = new ArrayList<double[]>();
	volatile protected TreeMap<Integer, ArrayList<Integer>> cent_to_record = null;

	
	
	public AbstractCentroidClusterer(AbstractRealMatrix data,
			CentroidClustererPlanner planner) {
		super(data, planner, planner.getK());
		
		this.maxIter = planner.getMaxIter();
		this.tolerance = planner.getConvergenceTolerance();
		this.m = data.getRowDimension();
		
		if(maxIter < 0)	throw new IllegalArgumentException("maxIter must exceed 0");
		if(tolerance<0)	throw new IllegalArgumentException("minChange must exceed 0");
		
		this.init_centroid_indices = initCentroids();
		logModelSummary();
	}
	
	@Override
	String modelSummary() {
		final ArrayList<Object[]> formattable = new ArrayList<>();
		formattable.add(new Object[]{
			"Num Rows","Num Cols","Metric","K","Scale","Force Par.","Allow Par.","Max Iter","Tolerance"
		});
		
		formattable.add(new Object[]{
			m,data.getColumnDimension(),getSeparabilityMetric(),k,normalized,
			GlobalState.ParallelismConf.FORCE_PARALLELISM_WHERE_POSSIBLE,
			GlobalState.ParallelismConf.ALLOW_AUTO_PARALLELISM,
			maxIter,
			tolerance
		});
		
		return formatter.format(formattable);
	}

	
	
	public static abstract class CentroidClustererPlanner 
			extends BaseClustererPlanner 
			implements UnsupervisedClassifierPlanner, ConvergeablePlanner {
		private static final long serialVersionUID = -1984508955251863189L;
		
		abstract public int getK();
		@Override abstract public int getMaxIter();
		@Override abstract public double getConvergenceTolerance();
		abstract public CentroidClustererPlanner setConvergenceCriteria(final double min);
	}
	



	/**
	 * Returns a matrix with a reference to centroids. Use with care.
	 * @return Array2DRowRealMatrix
	 */
	protected Array2DRowRealMatrix centroidsToMatrix() {
		double[][] c = new double[k][];
		
		int i = 0;
		for(double[] row: centroids)
			c[i++] = row;
		
		return new Array2DRowRealMatrix(c, false);
	}
	
	@Override
	public boolean didConverge() {
		return converged;
	}
	
	@Override
	public ArrayList<double[]> getCentroids() {
		final ArrayList<double[]> cent = new ArrayList<double[]>();
		for(double[] d : centroids)
			cent.add(VecUtils.copy(d));
		
		return cent;
	}
	
	/**
	 * Returns a copy of the classified labels
	 */
	@Override
	public int[] getLabels() {
		try {
			return VecUtils.copy(labels);
			
		} catch(NullPointerException npe) {
			String error = "model has not yet been fit";
			error(error);
			throw new ModelNotFitException(error);
		}
	}
	
	@Override
	public int getMaxIter() {
		return maxIter;
	}
	
	@Override
	public double getConvergenceTolerance() {
		return tolerance;
	}
	
	/**
	 * Returns the ordered indices of the centroids
	 * @return
	 */
	final private int[] initCentroids() {
		// Initialize centroids with K random records
		// Creates a list of integer sequence 0 -> nrow(data), then shuffles it
		// and takes the first K indices as the centroid records.
		final int[] recordIndices = VecUtils.permutation(VecUtils.arange(m), getSeed());
		
		final int[] cent_indices = new int[k];
		for(int i = 0; i < k; i++) {
			centroids.add(data.getRow(recordIndices[i]));
			cent_indices[i] = recordIndices[i];
		}
		
		return cent_indices;
	}
	
	/**
	 * In the corner case that k = 1, the {@link LabelEncoder}
	 * won't work, so we need to label everything as 0 and immediately return
	 */
	final void labelFromSingularK(final double[][] X) {
		labels = VecUtils.repInt(0, m);
		double[] center_record = MatUtils.meanRecord(X);
		
		tssCost = 0;
		double diff;
		for(double[] d: X) {
			for(int j = 0; j < data.getColumnDimension(); j++) {
				diff = d[j] - center_record[j];
				tssCost += diff * diff;
			}
		}
		
		converged = true;
		warn("k=1; converged immediately with a TSS of "+tssCost);
	}
	
	@Override
	public int itersElapsed() {
		return iter;
	}
	
	/** {@inheritDoc} */
	@Override
	public double indexAffinityScore(int[] labels) {
		// Propagates ModelNotFitException
		return UnsupervisedIndexAffinity.getInstance().evaluate(labels, getLabels());
	}

	/** {@inheritDoc} */
	@Override
	public double silhouetteScore() {
		return silhouetteScore(getSeparabilityMetric());
	}

	/** {@inheritDoc} */
	@Override
	public double silhouetteScore(GeometricallySeparable dist) {
		// Propagates ModelNotFitException
		return SilhouetteScore.getInstance().evaluate(this, dist, getLabels());
	}
	
	/**
	 * Return the cost of the entire clustering system. For KMeans, this
	 * equates to total sum of squares
	 * @return system cost
	 */
	public double totalCost() {
		return tssCost;
	}
	
	/**
	 * Reorder the labels in order of appearance using the 
	 * {@link LabelEncoder}. Also reorder the centroids to correspond
	 * with new label order
	 */
	abstract void reorderLabelsAndCentroids();
}
