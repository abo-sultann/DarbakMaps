package com.abosultan.darbakmaps;
import android.app.*;
import android.view.*;
import android.widget.*;
import com.abosultan.darbakmaps.data.PlaceRepository;

final class PointEditor {
    static void show(Activity a,double lat,double lon,PlaceRepository.Place existing){
        LinearLayout form=new LinearLayout(a);form.setOrientation(1);form.setPadding(24,12,24,12);form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView coords=new TextView(a);coords.setText(String.format(java.util.Locale.US,"%.6f, %.6f",lat,lon));form.addView(coords);
        String[] labels={"مخيم","بيت / استراحة","موقع السمان","ماء / بئر","شجرة / روضة","سيارة","مدخل / بوابة","عام"};
        String[] keys={"camp","home","quail","water","tree","car","gate","star"};
        Spinner type=new Spinner(a);type.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,labels));
        form.addView(type,new LinearLayout.LayoutParams(-1,56));
        EditText name=new EditText(a);name.setSingleLine(true);name.setHint("اسم الموقع");form.addView(name);
        EditText note=new EditText(a);note.setHint("ملاحظة اختيارية");note.setMaxLines(3);form.addView(note);
        if(existing!=null){name.setText(existing.name);note.setText(existing.note);for(int i=0;i<keys.length;i++)if(keys[i].equals(existing.iconKey))type.setSelection(i);}
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        AlertDialog d=new AlertDialog.Builder(a).setTitle(existing==null?"حفظ موقع":"تعديل الموقع").setView(scroll).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",null).create();
        d.show();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int k=type.getSelectedItemPosition();String title=name.getText().toString().trim();if(title.isEmpty())title=labels[k];
            try{
                PlaceRepository repo=new PlaceRepository(a);
                if(existing==null)repo.addDetailed(title,lat,lon,keys[k],labels[k],note.getText().toString());
                else if(!repo.update(existing.id,title,keys[k],labels[k],note.getText().toString()))throw new IllegalStateException("الموقع غير موجود");
                MapRuntimeBridge.refreshSavedPlaces(a);MapRuntimeBridge.showPoint(lat,lon);
                Toast.makeText(a,"تم حفظ الموقع",Toast.LENGTH_SHORT).show();d.dismiss();
            }catch(RuntimeException e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}
        });
    }
}
