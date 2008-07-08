package mpicbg.models;

import java.util.Collection;

public class RigidModel2D extends AffineModel2D
{

	static final protected int MIN_SET_SIZE = 2;
	
	@Override
	public int getMinSetSize(){ return MIN_SET_SIZE; }

	@Override
	public String toString()
	{
		return ( "[3,3](" + affine + ") " + cost );
	}

	final public void fit( Collection< PointMatch > matches ) throws NotEnoughDataPointsException
	{
		if ( matches.size() < MIN_SET_SIZE ) throw new NotEnoughDataPointsException( matches.size() + " data points are not enough to estimate a 2d rigid model, at least " + MIN_SET_SIZE + " data points required." );
		
		// Implementing Johannes Schindelin's squared error minimization formula
		// tan(angle) = Sum(x1*y1 + x2y2) / Sum(x1*y2 - x2*y1)
		
		// center of mass:
		float pcx = 0, pcy = 0;
		float qcx = 0, qcy = 0;
		
		double ws = 0.0;
		
		for ( PointMatch m : matches )
		{
			float[] p = m.getP1().getL(); 
			float[] q = m.getP2().getW();
			
			float w = m.getWeight();
			ws += w;
			
			pcx += w * p[ 0 ];
			pcy += w * p[ 1 ];
			qcx += w * q[ 0 ];
			qcy += w * q[ 1 ];
		}
		pcx /= ws;
		pcy /= ws;
		qcx /= ws;
		qcy /= ws;

		float dx = pcx - qcx;
		float dy = pcy - qcy;
		float sum1 = 0, sum2 = 0;
		float x1, y1, x2, y2;
		for ( PointMatch m : matches )
		{
			float[] p = m.getP1().getL(); 
			float[] q = m.getP2().getW();
			float w = m.getWeight();
			
			// make points local to the center of mass of the first landmark set
			x1 = p[ 0 ] - pcx; // x1
			y1 = p[ 1 ] - pcy; // x2
			x2 = q[ 0 ] - qcx + dx; // y1
			y2 = q[ 1 ] - qcy + dy; // y2
			sum1 += w * ( x1 * y2 - y1 * x2 ); //   x1 * y2 - x2 * y1 // assuming p1 is x1,x2 and p2 is y1,y2
			sum2 += w * ( x1 * x2 + y1 * y2 ); //   x1 * y1 + x2 * y2
		}
		float angle = ( float )Math.atan2( -sum1, sum2 );
		
		affine.setToIdentity();
		affine.rotate( -angle, qcx, qcy );
		affine.translate( -dx, -dy );
	}
	
	/**
	 * change the model a bit
	 * 
	 * estimates the necessary amount of shaking for each single dimensional
	 * distance in the set of matches
	 *
	 * @param matches point matches
	 * @param scale gives a multiplicative factor to each dimensional distance (increases the amount of shaking)
	 * @param center local pivot point
	 */
	final public void shake(
			Collection< PointMatch > matches,
			float scale,
			float[] center )
	{
		double xd = 0.0;
		double yd = 0.0;
		double rd = 0.0;
		
		int num_matches = matches.size();
		if ( num_matches > 0 )
		{
			for ( PointMatch m : matches )
			{
				float[] m_p1 = m.getP1().getW(); 
				float[] m_p2 = m.getP2().getW(); 
				
				xd += Math.abs( m_p1[ 0 ] - m_p2[ 0 ] );;
				yd += Math.abs( m_p1[ 1 ] - m_p2[ 1 ] );;
				
				// shift relative to the center
				float x1 = m_p1[ 0 ] - center[ 0 ];
				float y1 = m_p1[ 1 ] - center[ 1 ];
				float x2 = m_p2[ 0 ] - center[ 0 ];
				float y2 = m_p2[ 1 ] - center[ 1 ];
				
				float l1 = ( float )Math.sqrt( x1 * x1 + y1 * y1 );
				float l2 = ( float )Math.sqrt( x2 * x2 + y2 * y2 );

				x1 /= l1;
				x2 /= l2;
				y1 /= l1;
				y2 /= l2;

				//! unrotate (x1,y1)^T to (x2,y2)^T = (1,0)^T getting the sinus and cosinus of the rotation angle
				float cos = x1 * x2 + y1 * y2;
				float sin = y1 * x2 - x1 * y2;

				rd += Math.abs( Math.atan2( sin, cos ) );
			}
			xd /= matches.size();
			yd /= matches.size();
			rd /= matches.size();
			
			//System.out.println( rd );
		}
		
		affine.rotate( rnd.nextGaussian() * ( float )rd * scale, center[ 0 ], center[ 1 ] );
	}

	
	public RigidModel2D clone()
	{
		RigidModel2D trm = new RigidModel2D();
		trm.affine.setTransform( affine );
		trm.cost = cost;
		return trm;
	}

	public void preConcatenate(RigidModel2D model) {
		this.affine.preConcatenate(model.affine);
	}
	
	public void concatenate(RigidModel2D model) {
		this.affine.concatenate(model.affine);
	}
}
