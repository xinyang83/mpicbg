package mpicbg.models;

import ij.ImagePlus;
import ij.process.ByteProcessor;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 * @param <M> local model
 * 
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>
 * @version 0.1b
 */
public class ElasticMovingLeastSquaresMesh< M extends AbstractModel< M > > extends MovingLeastSquaresMesh< M >
{
	final protected HashSet< Tile< M > > fixedTiles = new HashSet< Tile< M > >();
	
	final static private DecimalFormat decimalFormat = new DecimalFormat();
	final static private DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols();
	
	final protected float alpha;
	
	public ElasticMovingLeastSquaresMesh(
			final Class< M > modelClass,
			final int numX,
			final int numY,
			final float width,
			final float height,
			final float alpha )
	{
		super( modelClass, numX, numY, width, height );
		
		decimalFormatSymbols.setGroupingSeparator( ',' );
		decimalFormatSymbols.setDecimalSeparator( '.' );
		decimalFormat.setDecimalFormatSymbols( decimalFormatSymbols );
		decimalFormat.setMaximumFractionDigits( 3 );
		decimalFormat.setMinimumFractionDigits( 3 );
		
		this.alpha = alpha;
		
		final Set< PointMatch > s = va.keySet();
		
		// temporary weights for inter-vertex PointMatches
		final float[] w = new float[ 2 ];
		w[ 0 ] = 1.0f / s.size();
		
		for ( final PointMatch vertex : s )
		{
			/**
			 * For each vertex, collect its connected vertices ...
			 * ... which are those connected by a triangle edge to it
			 */
			final HashSet< PointMatch > connectedVertices = new HashSet< PointMatch >();
			for ( final AffineModel2D ai : va.get( vertex ) )
			{
				for ( final PointMatch m : av.get( ai ) )
				{
					if ( vertex != m ) connectedVertices.add( m );
				}
			}
			/**
			 * ... which are all vertices instead this one itself
			 */
//			final HashSet< PointMatch > connectedVertices = new HashSet< PointMatch >();
//			connectedVertices.addAll( s );
//			connectedVertices.remove( vertex );
			/**
			 * Add PointMatches for each connectedVertex.
			 * These PointMatches work as "regularizers" for the mesh, that is
			 * the mesh tries to stay rigid.  The influence of these intra-stability
			 * points is given by weighting factors.
			 * 
			 * TODO
			 *   Currently, we assign two weights to each match:
			 *   1. a constant weight that defines the "stiffness" of the mesh
			 *   2. a weigh that depends on the distance to the vertex similar
			 *      to the moving least squares. 
			 * 
			 *   Outdated:
			 * 	 Currently we assign a weight of 1 / number of vertices to
			 *   each vertex.  That represents a "density" related weight
			 *   assuming the image has an area of 1.  This will give different
			 *   results for square and non-square images.
			 *   
			 *   Should we use min( number of vertical, number of horizontal)^2
			 *   instead?
			 */
			Tile< M > t = pt.get( vertex );
			for ( final PointMatch m : connectedVertices )
			{
				final Tile< M > o = pt.get( m );
				final Point p2 = m.getP2();
				final Point p1 = new Point( p2.getW().clone() );
				
				w[ 1 ] = weigh( Point.squareDistance( vertex.getP1(), p2 ), alpha );
				
				/*
				 * non weighted match
				 */
//				t.addMatch( new PointMatch( p1, p2 ) );
				
				/*
				 * weighted match
				 */
				t.addMatch( new PointMatch( p1, p2, w, 1.0f ) );				
				t.addConnectedTile( o );
			}
		}
	}
	
	public ElasticMovingLeastSquaresMesh(
			final Class< M > modelClass, 
			final int numX,
			final float width,
			final float height,
			final float alpha )
	{
		this( modelClass, numX, numY( numX, width, height ), width, height, alpha );
	}
	
	/**
	 * Update all PointMatches in all tiles and estimate the average
	 * displacement by weight of the PointMatch. 
	 */
	final public void update( final float amount )
	{
		final Set< PointMatch > s = va.keySet();
		
		for ( final PointMatch m : s )
		{
			final Tile< M > t = pt.get( m );
			
			/**
			 * Update the location of the vertex
			 */
			m.getP2().apply( t.getModel(), amount );
			
			/**
			 * Update the tile
			 */
			t.update( amount );
		}
		
		double cd = 0.0;
		double min_d = Double.MAX_VALUE;
		double max_d = Double.MIN_VALUE;
		for ( final PointMatch m : s )
		{
			final double d = pt.get( m ).getDistance();
			if ( d < min_d ) min_d = d;
			if ( d > max_d ) max_d = d;
			cd += d;
		}
		cd /= pt.size();
		error = cd;
	}
	
	/**
	 * Performs one optimization iteration and writes its error into the ErrorStatistics
	 * 
	 * @param observer collecting the error after update
	 * @throws NotEnoughDataPointsException
	 */
	public final void optimizeIteration(
			final ErrorStatistic observer )
		throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		final Set< PointMatch > s = va.keySet();
		
		error = 0.0;
		for ( final PointMatch m : s )
		{
			final Tile< M > t = pt.get( m );
			
			//System.out.println( t.getMatches().size() );
			if ( fixedTiles.contains( t ) ) continue;
			t.fitModel();
			t.update();
			
			/**
			 * Update the location of the vertex
			 */
			m.getP2().apply( t.getModel() );
			
			error += t.getDistance();
			updateAffine( m );
		}
		error /= s.size();
		
		observer.add( error );
	}
	
	/**
	 * Performs one optimization iteration.
	 * 
	 * @throws NotEnoughDataPointsException
	 */
	final public void optimizeIteration()
		throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		final Set< PointMatch > s = va.keySet();
		
		for ( final PointMatch m : s )
		{
			final Tile< M > t = pt.get( m );
			if ( fixedTiles.contains( t ) ) continue;
			
			t.fitModel();
		}
	}
	
	public void fixTile( final Tile< M > t )
	{
		fixedTiles.add( t );
	}
	
	/**
	 * Minimize the displacement of all PointMatches of all tiles.
	 * 
	 * @param maxError do not accept convergence if error is > max_error
	 * @param maxIterations stop after that many iterations even if there was
	 *   no minimum found
	 * @param maxPlateauwidth convergence is reached if the average slope in
	 *   an interval of this size is 0.0 (in double accuracy).  This prevents
	 *   the algorithm from stopping at plateaus smaller than this value.
	 */
	final public void optimize(
			final float maxError,
			final int maxIterations,
			final int maxPlateauwidth )
		throws NotEnoughDataPointsException, IllDefinedDataPointsException 
	{
		final ErrorStatistic observer = new ErrorStatistic( maxPlateauwidth + 1 );
		
		int i = 0;
		
		while ( i < maxIterations )  // do not run forever
		{
			optimizeIteration();
			update( 0.1f );
			observer.add( error );
			
			if ( i >= maxPlateauwidth && error < maxError &&
					Math.abs( observer.getWideSlope( maxPlateauwidth ) ) <= 0.00001 &&
					Math.abs( observer.getWideSlope( maxPlateauwidth / 2 ) ) <= 0.00001 )
				break;
			
			++i;
		}
		
		updateAffines();
		
		System.out.println( "Exiting at iteration " + i + " with error " + decimalFormat.format( observer.mean ) + " and slope " + observer.getWideSlope( maxPlateauwidth ) );
		System.out.println( "Successfully optimized configuration of " + pt.size() + " tiles:" );
		System.out.println( "  average displacement: " + decimalFormat.format( observer.mean ) + "px" );
		System.out.println( "  minimal displacement: " + decimalFormat.format( observer.min ) + "px" );
		System.out.println( "  maximal displacement: " + decimalFormat.format( observer.max ) + "px" );
	}
	
	/**
	 * Minimize the displacement of all PointMatches of all tiles.
	 * 
	 * @param maxError do not accept convergence if error is > max_error
	 * @param maxIterations stop after that many iterations even if there was
	 *   no minimum found
	 * @param maxPlateauwidth convergence is reached if the average slope in
	 *   an interval of this size is 0.0 (in double accuracy).  This prevents
	 *   the algorithm from stopping at plateaus smaller than this value.
	 */
	final public void optimizeByStrength(
			final float maxError,
			final int maxIterations,
			final int maxPlateauwidth,
			final ByteProcessor ipPlot,
			final ImagePlus impPlot )
		throws NotEnoughDataPointsException, IllDefinedDataPointsException 
	{
		final ErrorStatistic observer = new ErrorStatistic( maxPlateauwidth + 1 );
		
		int i = 0;
		
		ipPlot.setColor( Color.white );
		ipPlot.fill();
		impPlot.updateAndDraw();
		
		while ( i < maxIterations )  // do not run forever
		{
			optimizeIteration();
			update( 0.75f );
			observer.add( error );
			
			ipPlot.set(
					( int )( i * ( float )ipPlot.getWidth() / ( float )maxIterations ),
					//( int )( ( double )observer.values.get( observer.values.size() - 1 ) ),
					Math.min( ipPlot.getHeight() - 1, Math.max( 0, ipPlot.getHeight() / 2 - ( int )( Math.log( ( double )observer.values.get( observer.values.size() - 1 ) ) * 10 ) ) ),
					//( int )( 10 * error ),
					0 );
			impPlot.updateAndDraw();
			
			if ( i >= maxPlateauwidth && error < maxError &&
					Math.abs( observer.getWideSlope( maxPlateauwidth ) ) <= 0.0001 &&
					Math.abs( observer.getWideSlope( maxPlateauwidth / 2 ) ) <= 0.0001 )
				break;
			
			++i;
		}
		
		updateAffines();
		
		System.out.println( "Exiting at iteration " + i + " with error " + decimalFormat.format( observer.mean ) + " and slope " + decimalFormat.format( observer.getWideSlope( maxPlateauwidth ) ) );
		System.out.println( "Successfully optimized configuration of " + pt.size() + " vertices:" );
		System.out.println( "  average displacement: " + decimalFormat.format( observer.mean ) + "px" );
		System.out.println( "  minimal displacement: " + decimalFormat.format( observer.min ) + "px" );
		System.out.println( "  maximal displacement: " + decimalFormat.format( observer.max ) + "px" );
	}
	
	/**
	 * Create a Shape that illustrates the PointMatches.
	 * 
	 * @return the illustration
	 */
	final public Shape illustratePointMatches()
	{
		final Set< PointMatch > s = va.keySet();
		
		final GeneralPath path = new GeneralPath();
		
		for ( final PointMatch m : s )
		{
			final Tile< M > t = pt.get( m );
			for ( PointMatch ma : t.getMatches() )
			{
				final float[] l = ma.getP1().getW();
				
				path.moveTo( l[ 0 ] - 1, l[ 1 ] - 1 );
				path.lineTo( l[ 0 ] + 1, l[ 1 ] - 1 );
				path.lineTo( l[ 0 ] + 1, l[ 1 ] + 1 );
				path.lineTo( l[ 0 ] - 1, l[ 1 ] + 1 );
				path.closePath();
			}
		}
		return path;
	}
	
	/**
	 * Create a Shape that illustrates the displacements of PointMatches.
	 * 
	 * @return the illustration
	 */
	final public Shape illustratePointMatchDisplacements()
	{
		final Set< PointMatch > s = va.keySet();
		
		final GeneralPath path = new GeneralPath();
		
		for ( final PointMatch m : s )
		{
			final Tile< M > t = pt.get( m );
			for ( final PointMatch ma : t.getMatches() )
			{
				final float[] l = ma.getP1().getW();
				final float[] k = ma.getP2().getW();
				
				path.moveTo( l[ 0 ], l[ 1 ] );
				path.lineTo( k[ 0 ], k[ 1 ] );
			}
		}
		return path;
	}
	
}
