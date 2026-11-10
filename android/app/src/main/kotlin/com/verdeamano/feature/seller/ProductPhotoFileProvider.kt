package com.verdeamano.feature.seller

import androidx.core.content.FileProvider

// Android registers providers by component class; photos must not reuse the export component.
class ProductPhotoFileProvider : FileProvider()
