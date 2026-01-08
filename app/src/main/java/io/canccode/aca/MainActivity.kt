<?xml version="1.0" encoding="utf-8"?>
<androidx.drawerlayout.widget.DrawerLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/drawer_layout"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Main content -->
    <LinearLayout
        android:orientation="vertical"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <!-- Editor container -->
        <FrameLayout
            android:id="@+id/editor_container"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="3" />

        <!-- File browser container -->
        <FrameLayout
            android:id="@+id/file_browser_container"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="2" />

        <!-- LLM output panel -->
        <FrameLayout
            android:id="@+id/llm_container"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="2" />

        <!-- LLM input bar -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:padding="8dp">

            <EditText
                android:id="@+id/llm_input_field"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="Enter prompt"
                android:imeOptions="actionDone"
                android:inputType="textMultiLine" />

            <ImageButton
                android:id="@+id/fab_send"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:src="@android:drawable/ic_menu_send"
                android:contentDescription="Send" />
        </LinearLayout>
    </LinearLayout>

    <!-- Navigation drawer -->
    <com.google.android.material.navigation.NavigationView
        android:id="@+id/nav_view"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        app:menu="@menu/drawer_menu" />

</androidx.drawerlayout.widget.DrawerLayout>