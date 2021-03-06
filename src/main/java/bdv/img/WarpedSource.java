package bdv.img;

import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import bdv.img.cache.CacheHints;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.render.DefaultMipmapOrdering;
import bdv.viewer.render.MipmapOrdering;
import bdv.viewer.render.SetCacheHints;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.view.Views;

public class WarpedSource < T > implements Source< T >, MipmapOrdering, SetCacheHints
{

	public static < T > SourceAndConverter< T > wrap( final SourceAndConverter< T > wrap, final String name, int ndims )
	{
		return new SourceAndConverter< T >(
				new WarpedSource< T >( wrap.getSpimSource(), name ),
				wrap.getConverter(),
				wrap.asVolatile() == null ? null : wrap( wrap.asVolatile(), name, ndims ) );
	}

	/**
	 * The wrapped {@link Source}.
	 */
	private final Source< T > source;

	private final String name;

	/**
	 * This is either the {@link #source} itself, if it implements
	 * {@link MipmapOrdering}, or a {@link DefaultMipmapOrdering}.
	 */
	private final MipmapOrdering sourceMipmapOrdering;

	/**
	 * This is either the {@link #source} itself, if it implements
	 * {@link SetCacheHints}, or a {@link SetCacheHints} doing
	 * nothing.
	 */
	private final SetCacheHints sourceSetCacheHints;
	
	private final TpsTransformWrapper tpsXfm;
	
	private AffineTransform3D viewXfm;

	private boolean isTransformed;
	
	public WarpedSource( final Source< T > source, final String name )
	{
		this.source = source;
		this.name = name;
		this.isTransformed = false;
		
		this.tpsXfm = new TpsTransformWrapper( 3 );

		sourceMipmapOrdering = MipmapOrdering.class.isInstance( source ) ?
				( MipmapOrdering ) source : new DefaultMipmapOrdering( source );

		sourceSetCacheHints = SetCacheHints.class.isInstance( source ) ?
				( SetCacheHints ) source : SetCacheHints.empty;
	}
	
	public void setViewTransform( AffineTransform3D viewXfm )
	{
		this.viewXfm = viewXfm;
	}

	@Override
	public boolean isPresent( final int t )
	{
		return source.isPresent( t );
	}
	
	public void updateTransform( ThinPlateR2LogRSplineKernelTransform tps )
	{
		//a deep copy of the transform is used internally
		tpsXfm.setTps( tps );
	}
	
	public void setIsTransformed( boolean isTransformed )
	{
		this.isTransformed = isTransformed;
	}
	
	public boolean isTransformed( )
	{
		return isTransformed;
	}

	@Override
	public RandomAccessibleInterval< T > getSource( final int t, final int level )
	{
		return Views.interval(
				Views.raster( getInterpolatedSource( t, level, Interpolation.NEARESTNEIGHBOR ) ),
				estimateBoundingInterval( t, level ));
	}

	private Interval estimateBoundingInterval( final int t, final int level )
	{
		final Interval wrappedInterval = source.getSource( t, level );
		// TODO: Do something meaningful: apply transform, estimate bounding box, etc.
		return wrappedInterval;
	}

	@Override
	public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
	{
		// System.out.println("WS: getInterpolatedSource");
		final AffineTransform3D transform = new AffineTransform3D();
		source.getSourceTransform( t, level, transform );
		final RealRandomAccessible< T > sourceRealAccessible = RealViews.affineReal( source.getInterpolatedSource( t, level, method ), transform );

		if( isTransformed )
			return RealViews.transformReal( sourceRealAccessible, tpsXfm );
		else
			return sourceRealAccessible;
	}

	@Override
	public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
	{
		transform.identity();
	}

	@Override
	public AffineTransform3D getSourceTransform( final int t, final int level )
	{
		return new AffineTransform3D();
	}

	@Override
	public T getType()
	{
		return source.getType();
	}

	@Override
	public String getName()
	{
		return source.getName() + "_" + name;
	}

	@Override
	public VoxelDimensions getVoxelDimensions()
	{
		return source.getVoxelDimensions();
	}

	@Override
	public int getNumMipmapLevels()
	{
		return source.getNumMipmapLevels();
	}

	@Override
	public void setCacheHints( final int level, final CacheHints cacheHints )
	{
		sourceSetCacheHints.setCacheHints( level, cacheHints );
	}

	@Override
	public synchronized MipmapHints getMipmapHints( final AffineTransform3D screenTransform, final int timepoint, final int previousTimepoint )
	{
		return sourceMipmapOrdering.getMipmapHints( screenTransform, timepoint, previousTimepoint );
	}
}
