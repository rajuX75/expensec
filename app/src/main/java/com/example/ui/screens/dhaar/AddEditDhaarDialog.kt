package com.example.ui.screens.dhaar

import android.app.DatePickerDialog
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AccountEntity
import com.example.data.model.Contact
import com.example.data.model.DhaarEntry
import com.example.ui.theme.Emerald400
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDhaarDialog(
    viewModel: ExpenseViewModel,
    initialEntry: DhaarEntry? = null,
    preselectedContactId: Long? = null,
    preselectedType: String? = null, // "GIVEN", "RECEIVED"
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val currencyCode by viewModel.currency.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()

    var selectedContactId by remember {
        mutableStateOf(initialEntry?.contactId ?: preselectedContactId ?: allContacts.firstOrNull()?.id ?: 0L)
    }
    var entryType by remember {
        mutableStateOf(initialEntry?.type ?: preselectedType ?: "GIVEN")
    }
    var amountText by remember {
        mutableStateOf(initialEntry?.let { String.format(Locale.US, "%.2f", it.amount) } ?: "")
    }
    var note by remember { mutableStateOf(initialEntry?.note ?: "") }
    var selectedDate by remember { mutableStateOf(initialEntry?.date ?: System.currentTimeMillis()) }
    var hasDueDate by remember { mutableStateOf(initialEntry?.dueDate != null) }
    var selectedDueDate by remember {
        mutableStateOf(initialEntry?.dueDate ?: (System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L))
    }
    var photoUriString by remember { mutableStateOf<String?>(initialEntry?.tagPhotoUri) }

    var linkToAccount by remember { mutableStateOf(initialEntry?.linkedAccountId != null) }
    var selectedAccountId by remember {
        mutableStateOf(initialEntry?.linkedAccountId ?: allAccounts.firstOrNull()?.id ?: 1L)
    }

    var showNewContactDialog by remember { mutableStateOf(false) }
    var contactDropdownExpanded by remember { mutableStateOf(false) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var contactError by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }

    // System Image Picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { photoUriString = it.toString() }
    }

    // Contact Picker from Phone Contacts
    val phoneContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            val cursor = context.contentResolver.query(contactUri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val contactName = if (nameIndex != -1) it.getString(nameIndex) else "Phone Contact"
                    val contactSysId = if (idIndex != -1) it.getString(idIndex) else ""

                    // Fetch phone number if available
                    var contactPhone: String? = null
                    val phoneCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactSysId),
                        null
                    )
                    phoneCursor?.use { pCur ->
                        if (pCur.moveToFirst()) {
                            val phoneIdx = pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (phoneIdx != -1) contactPhone = pCur.getString(phoneIdx)
                        }
                    }

                    // Check if already in list or insert
                    val existing = allContacts.find { c -> c.name.equals(contactName, ignoreCase = true) }
                    if (existing != null) {
                        selectedContactId = existing.id
                    } else {
                        viewModel.addContact(Contact(name = contactName, phoneNumber = contactPhone)) { newId ->
                            selectedContactId = newId
                        }
                    }
                }
            }
        }
    }

    val selectedContact = remember(selectedContactId, allContacts) {
        allContacts.find { it.id == selectedContactId }
    }

    val selectedAccount = remember(selectedAccountId, allAccounts) {
        allAccounts.find { it.id == selectedAccountId } ?: allAccounts.firstOrNull()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialEntry == null) "Add Debt / Loan Record" else "Edit Record",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Type Toggle: Gave vs Received
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Gave (Lent)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { entryType = "GIVEN" },
                    color = if (entryType == "GIVEN") IncomeGreen else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ArrowOutward,
                            contentDescription = null,
                            tint = if (entryType == "GIVEN") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lent / Gave",
                            fontWeight = FontWeight.Bold,
                            color = if (entryType == "GIVEN") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Received (Borrowed)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { entryType = "RECEIVED" },
                    color = if (entryType == "RECEIVED") ExpenseRed else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CallReceived,
                            contentDescription = null,
                            tint = if (entryType == "RECEIVED") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Borrowed / Received",
                            fontWeight = FontWeight.Bold,
                            color = if (entryType == "RECEIVED") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Contact Picker
            Column {
                Text(
                    text = "Contact *",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = contactDropdownExpanded,
                    onExpandedChange = { contactDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedContact?.name ?: "Select Contact",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = contactDropdownExpanded) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        isError = contactError && selectedContact == null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = contactDropdownExpanded,
                        onDismissRequest = { contactDropdownExpanded = false }
                    ) {
                        allContacts.forEach { contact ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(contact.name, fontWeight = FontWeight.SemiBold)
                                        if (!contact.phoneNumber.isNullOrBlank()) {
                                            Text(contact.phoneNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = {
                                    selectedContactId = contact.id
                                    contactDropdownExpanded = false
                                    contactError = false
                                }
                            )
                        }
                    }
                }

                // Quick buttons: New contact or pick from phone
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showNewContactDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ New Contact", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                phoneContactPickerLauncher.launch(null)
                            } catch (e: Exception) {
                                // Fallback silently
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("From Contacts", fontSize = 12.sp)
                    }
                }
            }

            // Amount Input
            Column {
                Text(
                    text = "Amount *",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        if (it.isNotBlank()) amountError = false
                    },
                    leadingIcon = {
                        Text(
                            text = currencySymbol,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    placeholder = { Text("0.00") },
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Date & Due Date Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Transaction Date
                OutlinedCard(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selected = Calendar.getInstance().apply {
                                    set(y, m, d)
                                }
                                selectedDate = selected.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dateFormat.format(Date(selectedDate)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                // Due Date
                OutlinedCard(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDueDate }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val selected = Calendar.getInstance().apply {
                                    set(y, m, d)
                                }
                                selectedDueDate = selected.timeInMillis
                                hasDueDate = true
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Due Date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasDueDate) dateFormat.format(Date(selectedDueDate)) else "Add Due Date",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (hasDueDate) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (hasDueDate) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // Note Input
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note / Description") },
                placeholder = { Text("e.g. Lunch share, emergency loan") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Photo Attachment
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { imagePickerLauncher.launch("image/*") },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (photoUriString != null) "Photo Attached" else "Attach Receipt / Note Photo",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            if (photoUriString != null) {
                                Text("Tap to replace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (photoUriString != null) {
                        IconButton(
                            onClick = { photoUriString = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove photo", tint = ExpenseRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Link to Main Account Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Link to Account",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Affects selected account balance automatically",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = linkToAccount,
                            onCheckedChange = { linkToAccount = it }
                        )
                    }

                    if (linkToAccount) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ExposedDropdownMenuBox(
                            expanded = accountDropdownExpanded,
                            onExpandedChange = { accountDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedAccount?.name ?: "Select Account",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Account") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = accountDropdownExpanded,
                                onDismissRequest = { accountDropdownExpanded = false }
                            ) {
                                allAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = {
                                            selectedAccountId = acc.id
                                            accountDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        amountError = true
                        return@Button
                    }
                    if (selectedContact == null) {
                        contactError = true
                        return@Button
                    }

                    val entry = DhaarEntry(
                        id = initialEntry?.id ?: 0L,
                        uuid = initialEntry?.uuid ?: UUID.randomUUID().toString(),
                        contactId = selectedContact.id,
                        type = entryType,
                        amount = amt,
                        currencyCode = currencyCode,
                        date = selectedDate,
                        dueDate = if (hasDueDate) selectedDueDate else null,
                        note = note.trim(),
                        tagPhotoUri = photoUriString,
                        linkedAccountId = if (linkToAccount) selectedAccountId else null,
                        isSettlementGive = null
                    )

                    if (initialEntry == null) {
                        viewModel.addDhaarEntry(
                            entry = entry,
                            linkToAccount = linkToAccount,
                            accountName = selectedAccount?.name
                        )
                    } else {
                        viewModel.updateDhaarEntry(entry)
                    }

                    onSaved()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (initialEntry == null) "Save Record" else "Update Record",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    if (showNewContactDialog) {
        AddEditContactDialog(
            viewModel = viewModel,
            onDismiss = { showNewContactDialog = false },
            onContactSaved = { newId ->
                selectedContactId = newId
            }
        )
    }
}
