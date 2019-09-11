package de.retest.web;

import static de.retest.web.ScreenshotProvider.shoot;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.retest.recheck.RecheckAdapter;
import de.retest.recheck.ui.DefaultValueFinder;
import de.retest.recheck.ui.descriptors.RootElement;
import de.retest.recheck.ui.descriptors.idproviders.RetestIdProvider;
import de.retest.recheck.util.RetestIdProviderUtil;
import de.retest.web.mapping.PathsToWebDataMapping;
import de.retest.web.selenium.UnbreakableDriver;

public class RecheckSeleniumAdapter implements RecheckAdapter {

	private static final String GET_ALL_ELEMENTS_BY_PATH_JS_PATH = "/javascript/getAllElementsByPath.js";

	private static final Logger logger = LoggerFactory.getLogger( RecheckSeleniumAdapter.class );

	private final DefaultValueFinder defaultValueFinder = new DefaultWebValueFinder();

	private final RetestIdProvider retestIdProvider;
	private final AttributesProvider attributesProvider;

	public RecheckSeleniumAdapter( final RetestIdProvider retestIdProvider,
			final AttributesProvider attributesProvider ) {
		this.retestIdProvider = retestIdProvider;
		this.attributesProvider = attributesProvider;
		logger.debug( "New RecheckSeleniumAdapter created: {}.", System.identityHashCode( this ) );
	}

	public RecheckSeleniumAdapter() {
		this( RetestIdProviderUtil.getConfiguredRetestIdProvider(), YamlAttributesProvider.getInstance() );
	}

	@Override
	public boolean canCheck( final Object toVerify ) {
		return toVerify instanceof WebDriver || toVerify instanceof RemoteWebElement //
				|| toVerify instanceof WrapsElement && canCheck( ((WrapsElement) toVerify).getWrappedElement() );
	}

	@Override
	public Set<RootElement> convert( final Object toVerify ) {
		if ( toVerify instanceof WebDriver ) {
			return convert( (WebDriver) toVerify );
		}
		if ( toVerify instanceof RemoteWebElement ) {
			return convert( (RemoteWebElement) toVerify );
		}
		if ( toVerify instanceof WrapsElement ) {
			return convert( ((WrapsElement) toVerify).getWrappedElement() );
		}
		throw new IllegalArgumentException( "Cannot convert objects of " + toVerify.getClass() );
	}

	public Set<RootElement> convert( final RemoteWebElement webElement ) {
		logger.info( "Retrieving attributes for element '{}'.", webElement );
		return convert( webElement.getWrappedDriver(), webElement );
	}

	public Set<RootElement> convert( final WebDriver driver ) {
		logger.info( "Retrieving attributes for each element." );
		return convert( driver, null );
	}

	private Set<RootElement> convert( final WebDriver driver, final RemoteWebElement node ) {
		final Set<String> cssAttributes = attributesProvider.getCssAttributes();
		final JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
		@SuppressWarnings( "unchecked" )
		final Map<String, Map<String, Object>> tagMapping =
				(Map<String, Map<String, Object>>) jsExecutor.executeScript( getQueryJS(), cssAttributes, node );
		final RootElement lastChecked =
				convert( tagMapping, driver.getCurrentUrl(), driver.getTitle(), shoot( driver ) );

		final FrameConverter frameConverter =
				new FrameConverter( getQueryJS(), retestIdProvider, attributesProvider, defaultValueFinder );
		frameConverter.addChildrenFromFrames( driver, cssAttributes, lastChecked );

		if ( driver instanceof UnbreakableDriver ) {
			((UnbreakableDriver) driver).setLastActualState( lastChecked );
		}

		return Collections.singleton( lastChecked );
	}

	public RootElement convert( final Map<String, Map<String, Object>> tagMapping, final String url, final String title,
			final BufferedImage screenshot ) {
		final PathsToWebDataMapping mapping = new PathsToWebDataMapping( tagMapping );

		logger.info( "Checking website {} with {} elements.", url, mapping.size() );
		return new PeerConverter( retestIdProvider, attributesProvider, mapping, title, screenshot, defaultValueFinder,
				mapping.getRootPath() ).convertToPeers();
	}

	private String getQueryJS() {
		try ( final InputStream url = getClass().getResourceAsStream( GET_ALL_ELEMENTS_BY_PATH_JS_PATH ) ) {
			return String.join( "\n", IOUtils.readLines( url, StandardCharsets.UTF_8 ) );
		} catch ( final IOException e ) {
			throw new UncheckedIOException( "Exception reading '" + GET_ALL_ELEMENTS_BY_PATH_JS_PATH + "'.", e );
		}
	}

	@Override
	public DefaultValueFinder getDefaultValueFinder() {
		return defaultValueFinder;
	}

}
