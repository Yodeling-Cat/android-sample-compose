package uno.lux.mosaic.testing

import org.junit.Rule

abstract class ViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
}
