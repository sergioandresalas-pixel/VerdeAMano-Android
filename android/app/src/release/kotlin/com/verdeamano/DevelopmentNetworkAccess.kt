package com.verdeamano

import androidx.compose.runtime.Composable

// Production has no local-network permission or development consent screen.
@Composable fun DevelopmentNetworkAccess(content: @Composable () -> Unit) = content()
