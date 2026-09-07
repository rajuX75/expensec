package com.rjx.expensex.ui.common.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shared labelled form field. Replaces the 44 raw OutlinedTextField call-sites'
 * common boilerplate (label slot, single-line, supporting/error text).
 */
@Composable
fun AppFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { msg -> { Text(msg) } },
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        modifier = modifier
    )
}

/**
 * Password / API-secret field with a built-in visibility toggle.
 * Extracted from CloudinarySyncCard's hand-rolled show/hide-secret logic.
 */
@Composable
fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    var showSecret by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showSecret = !showSecret }) {
                Icon(
                    imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showSecret) "Hide secret" else "Show secret"
                )
            }
        },
        supportingText = supportingText?.let { msg -> { Text(msg) } },
        keyboardOptions = keyboardOptions,
        modifier = modifier
    )
}
