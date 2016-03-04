package com.clust4j.algo;

import java.util.Random;

import org.apache.commons.math3.linear.AbstractRealMatrix;

import com.clust4j.GlobalState;
import com.clust4j.algo.NearestNeighborHeapSearch.Neighborhood;
import com.clust4j.algo.preprocess.FeatureNormalization;
import com.clust4j.except.ModelNotFitException;
import com.clust4j.log.LogTimer;
import com.clust4j.metrics.pairwise.GeometricallySeparable;
import com.clust4j.utils.MatUtils;
import com.clust4j.utils.VecUtils;

public class NearestNeighbors extends Neighbors {
	private static final long serialVersionUID = 8306843374522289973L;
	
	
	
	
	public NearestNeighbors(AbstractRealMatrix data) {
		this(data, DEF_K);
	}

	public NearestNeighbors(AbstractRealMatrix data, int k) {
		this(data, new NearestNeighborsPlanner(k));
	}

	public NearestNeighbors(AbstractRealMatrix data, NearestNeighborsPlanner planner) {
		super(data, planner);
		if(kNeighbors < 1) throw new IllegalArgumentException("k must be positive");
		if(kNeighbors > m) throw new IllegalArgumentException("k must be <= number of samples");
		logModelSummary();
	}
	
	private static void validateK(int k, int m) {
		if(k < 1) throw new IllegalArgumentException("k must be positive");
		if(k > m) throw new IllegalArgumentException("k must be < number of samples");
	}
	
	@Override
	final protected ModelSummary modelSummary() {
		return new ModelSummary(new Object[]{
				"Num Rows","Num Cols","Metric","Algo","K","Scale","Force Par.","Allow Par."
			}, new Object[]{
				m,data.getColumnDimension(),getSeparabilityMetric(),
				alg, kNeighbors, normalized,
				GlobalState.ParallelismConf.FORCE_PARALLELISM_WHERE_POSSIBLE,
				GlobalState.ParallelismConf.ALLOW_AUTO_PARALLELISM
			});
	}
	
	
	
	
	public static class NearestNeighborsPlanner extends NeighborsPlanner {
		private Algorithm algo = DEF_ALGO;
		private GeometricallySeparable dist= NearestNeighborHeapSearch.DEF_DIST;
		private FeatureNormalization norm = DEF_NORMALIZER;
		private boolean verbose = DEF_VERBOSE;
		private boolean scale = DEF_SCALE;
		private Random seed = DEF_SEED;
		private final int k;
		private int leafSize = DEF_LEAF_SIZE;
		
		
		public NearestNeighborsPlanner() { this(DEF_K); }
		public NearestNeighborsPlanner(int k) {
			this.k = k;
		}
		

		
		@Override
		public NearestNeighbors buildNewModelInstance(AbstractRealMatrix data) {
			return new NearestNeighbors(data, this.copy());
		}

		@Override
		public NearestNeighborsPlanner setAlgorithm(Algorithm algo) {
			this.algo = algo;
			return this;
		}

		@Override
		public Algorithm getAlgorithm() {
			return algo;
		}

		@Override
		public NearestNeighborsPlanner copy() {
			return new NearestNeighborsPlanner(k)
				.setAlgorithm(algo)
				.setNormalizer(norm)
				.setScale(scale)
				.setSeed(seed)
				.setSep(dist)
				.setVerbose(verbose)
				.setLeafSize(leafSize);
		}
		
		@Override
		public int getLeafSize() {
			return leafSize;
		}
		
		@Override
		final public Integer getK() {
			return k;
		}

		@Override
		final public Double getRadius() {
			return null;
		}
		
		@Override
		public FeatureNormalization getNormalizer() {
			return norm;
		}

		@Override
		public GeometricallySeparable getSep() {
			return dist;
		}

		@Override
		public boolean getScale() {
			return scale;
		}

		@Override
		public Random getSeed() {
			return seed;
		}

		@Override
		public boolean getVerbose() {
			return verbose;
		}

		public NearestNeighborsPlanner setLeafSize(int leafSize) {
			this.leafSize = leafSize;
			return this;
		}
		
		@Override
		public NearestNeighborsPlanner setNormalizer(FeatureNormalization norm) {
			this.norm = norm;
			return this;
		}

		@Override
		public NearestNeighborsPlanner setScale(boolean b) {
			this.scale = b;
			return this;
		}

		@Override
		public NearestNeighborsPlanner setSeed(Random rand) {
			this.seed= rand;
			return this;
		}

		@Override
		public NearestNeighborsPlanner setVerbose(boolean b) {
			this.verbose = b;
			return this;
		}

		@Override
		public NearestNeighborsPlanner setSep(GeometricallySeparable dist) {
			this.dist = dist;
			return this;
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if(this == o)
			return true;
		if(o instanceof NearestNeighbors) {
			NearestNeighbors other = (NearestNeighbors)o;
			
			
			return 
				((null == other.kNeighbors || null == this.kNeighbors) ?
					other.kNeighbors == this.kNeighbors : 
						other.kNeighbors.intValue() == this.kNeighbors)
				&& other.leafSize == this.leafSize
				&& MatUtils.equalsExactly(other.fit_X, this.fit_X);
		}
		
		return false;
	}
	
	@Override
	public String getName() {
		return "NearestNeighbors";
	}
	
	public int getK() {
		return kNeighbors;
	}

	@Override
	public NearestNeighbors fit() {
		synchronized(this) {
			try {
				if(null != res)
					return this;

				info("Model fit:");
				int nNeighbors = kNeighbors + 1;
				final LogTimer timer = new LogTimer();
				
				info("querying tree for nearest neighbors");
				Neighborhood initRes = new Neighborhood(
					tree.query(fit_X, nNeighbors, 
						DUAL_TREE_SEARCH, SORT));

				
				double[][] dists = initRes.getDistances();
				int[][] indices  = initRes.getIndices();
				int i, j, ni = indices[0].length;
				
				
				// Set up sample range
				int[] sampleRange = VecUtils.arange(m);
				
				
				boolean allInRow, bval;
				boolean[] dupGroups = new boolean[m];
				boolean[][] sampleMask= new boolean[m][ni];
				for(i = 0; i < m; i++) {
					allInRow = true;
					
					for(j = 0; j < ni; j++) {
						bval = indices[i][j] != sampleRange[i];
						sampleMask[i][j] = bval;
						allInRow &= bval;
					}
					
					dupGroups[i] = allInRow; // duplicates in row?
				}
				
				
				// Comment from SKLEARN:
				// Corner case: When the number of duplicates are more
		        // than the number of neighbors, the first NN will not
		        // be the sample, but a duplicate.
		        // In that case mask the first duplicate.
				// sample_mask[:, 0][dup_gr_nbrs] = False
				
				info("identifying duplicate neighbors");
				for(i = 0; i < m; i++)
					if(dupGroups[i])
						sampleMask[i][0] = false;
				
				
				// Build output indices
				int k = 0;
				int[] indOut = new int[m * (nNeighbors - 1)];
				double[] distOut = new double[m * (nNeighbors - 1)];
				for(i = 0; i < m; i++) {
					for(j = 0; j < ni; j++) {
						if(sampleMask[i][j]) {
							indOut[k] = indices[i][j];
							distOut[k]= dists[i][j];
							k++;
						}
					}
				}
				
				info("computing neighborhoods");
				res = new Neighborhood(
					MatUtils.reshape(distOut, m, nNeighbors - 1),
					MatUtils.reshape(indOut,  m, nNeighbors - 1));
				
				
				sayBye(timer);
				return this;
			} catch(OutOfMemoryError | StackOverflowError e) {
				error(e.getLocalizedMessage() + " - ran out of memory during model fitting");
				throw e;
			} // end try/catch
			
		} // End synch
	}
	
	@Override
	final protected Object[] getModelFitSummaryHeaders() {
		// TODO
		return new Object[]{
			"TODO"
		};
	}
	
	@Override
	public Neighborhood getNeighbors(AbstractRealMatrix x) {
		return getNeighbors(x, kNeighbors);
	}
	
	public Neighborhood getNeighbors(AbstractRealMatrix x, int k) {
		if(null == res)
			throw new ModelNotFitException("model not yet fit");
		double[][] X = x.getData();
		
		validateK(k, X.length);
		return new Neighborhood(tree.query(X, k, 
			DUAL_TREE_SEARCH, SORT));
	}
}
